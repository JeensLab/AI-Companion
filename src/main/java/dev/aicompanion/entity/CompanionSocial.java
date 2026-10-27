package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Blocks;
import dev.aicompanion.CompanionConversation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.LinkedHashMap;
import java.util.Map;

/** Actual observations only. A chance, per-event deduplication and a global cooldown limit chatter. */
final class CompanionSocial {
    private final CompanionEntity owner;
    private final Map<String, Long> seen = new LinkedHashMap<>();
    private long nextSpeech;
    private boolean initialized;
    private int timeBand = -1;
    private boolean rain;
    private boolean environmentInitialized;
    CompanionSocial(CompanionEntity owner) { this.owner = owner; }
    private long next(long now) { return now + CompanionConversation.gap(owner.getRandom().nextInt(CompanionConversation.MAX_GAP - CompanionConversation.MIN_GAP + 1)); }
    void observe(String key, String fact) {
        offer(key, fact, 20 * 60 * 20);
    }
    private boolean offer(String key, String fact, int topicGap) {
        long now = owner.level().getGameTime();
        if (!initialized) { initialized = true; nextSpeech = next(now); }
        if (!owner.talksOnOwn() || !owner.conversationAvailable() || !owner.sociallySafe() || now < nextSpeech) return false;
        if (seen.containsKey(key) && now - seen.get(key) < topicGap) return false;
        // A missed random chance must not silence this topic for the next twenty minutes.
        // Stop selecting topics after an eligible attempt, even when the chance misses.
        if (owner.getRandom().nextFloat() > .65F) return true;
        seen.put(key, now);
        while (seen.size() > 32) seen.remove(seen.keySet().iterator().next());
        emit(fact);
        return true;
    }
    private void emit(String fact) {
        nextSpeech = next(owner.level().getGameTime());
        owner.socialEvent(fact);
    }
    void tick() {
        if (owner.tickCount % 40 != 0 || !owner.talksOnOwn() || !owner.isAlive()) return;
        long now = owner.level().getGameTime();
        if (!initialized) { initialized = true; nextSpeech = next(now); }
        int band = owner.level().isDay() ? 0 : 1;
        if (!environmentInitialized) { timeBand = band; rain = owner.level().isRaining(); environmentInitialized = true; }
        if (timeBand >= 0 && band != timeBand) observe("time:" + band, band == 1 ? "В мире наступает ночь." : "В мире наступило утро.");
        timeBand = band;
        if (owner.level().isRaining() && !rain) observe("rain", "Начался дождь.");
        rain = owner.level().isRaining();
        var threats = owner.level().getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(8),
                monster -> CompanionHostiles.observed(owner, monster, 8));
        // Threats are for actions and facial state, not a paid conversation opportunity.
        if (!threats.isEmpty()) return;
        if (owner.tickCount % 100 == 0) {
            BlockPos feet = owner.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-4, -2, -4), feet.offset(4, 2, 4))) {
                if (!owner.level().hasChunkAt(pos)) continue;
                var state = owner.level().getBlockState(pos);
                if ((state.is(Blocks.COAL_ORE) || state.is(Blocks.IRON_ORE) || state.is(Blocks.COPPER_ORE))
                        && CompanionMiningNavigation.visible(owner, owner.getEyePosition(), pos)) { observe("ore", "Видна руда: " + state.getBlock().getName().getString() + ". Я её пока не добывал."); break; }
            }
        }
        var player = owner.getOwnerPlayer();
        if (player != null && owner.distanceToSqr(player) < 12 * 12 && owner.tickCount >= CompanionConversation.MIN_GAP) {
            // Ordinary company is repeatable; a specific discovery still has its twenty-minute guard.
            int topicGap = 20 * 60 * 4;
            String activity = owner.getActivityForAi();
            if (activity.length() > 180) activity = activity.substring(0, 180);
            if (owner.hasActiveWorkOrder() && offer("company:work", "Товарищ рядом, я продолжаю работу: " + activity
                    + ". Можно перекинуться словом о занятии, не отчитываясь о каждой партии; завершение дела не подтверждено.", topicGap)) return;
            if (!player.getMainHandItem().isEmpty() && offer("company:equipment", "Товарищ рядом, в его руке сейчас: "
                    + player.getMainHandItem().getHoverName().getString() + ". Можно высказать отношение к этому предмету, без выдуманной истории его получения.", topicGap)) return;
            if (owner.level().isRaining() && offer("company:weather", "Мы рядом, сейчас идёт дождь. Начался ли он только что и есть ли у нас крыша — неизвестно.", topicGap)) return;
            String memory = owner.deeds.conversationMemory(now);
            if (!memory.isEmpty() && offer("company:memory", "Мы рядом. Можно вспомнить подтверждённое прошлое дело из журнала, "
                    + "если оно связано с разговором; не повторяй жалобы и перечень добычи. Прошлые записи: "
                    + memory, topicGap)) return;
            if (offer("company:light", "Мы рядом. Повод поделиться своим отношением к факелам и пути обратно из шахты. "
                    + "Это разговор о предпочтениях, не утверждение, что мы сейчас в шахте или без света.", topicGap)) return;
            if (offer("company:order", "Мы рядом. Повод поделиться мыслью о порядке в сундуках и запасах для выживания. "
                    + "Состояние наших сундуков и нехватка ресурсов этим не подтверждены.", topicGap)) return;
            offer("company:company", "Мы рядом. Моя текущая активность: " + activity
                    + ". Можно продолжить недавнюю тему, ответить на прежний стёб или поделиться спокойной мыслью о совместном выживании. Вопрос о планах не обязателен.", topicGap);
        }
    }

    void reactToSpeech(String text) {
        if (!owner.level().isClientSide) owner.expressions.react(dev.aicompanion.CompanionExpression.speech(text), owner.level().getGameTime(), 120);
    }
    void updateExpression() {
        if (owner.tickCount % 20 == 0) {
            var player = owner.getOwnerPlayer();
            var threats = owner.level().getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(12),
                    monster -> CompanionHostiles.observed(owner, monster, 12)
                            || player != null && CompanionHostiles.observed(player, monster, 12));
            owner.nearbySocialThreat = !threats.isEmpty() || player != null && player.level() == owner.level()
                    && !owner.level().getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(12),
                            monster -> CompanionHostiles.observed(player, monster, 12)).isEmpty();
            owner.nearbyExplosiveThreat = threats.stream().anyMatch(monster -> monster instanceof net.minecraft.world.entity.monster.Creeper creeper
                    && (creeper.getSwellDir() > 0 || creeper.distanceToSqr(owner) < 36));
        }
        boolean danger = owner.getHealth() <= owner.getMaxHealth() * .35F || owner.isOnFire() || owner.isInLava() || owner.isUnderWater() && owner.getAirSupply() < 40
                || owner.recovery.active() || owner.nearbyExplosiveThreat;
        boolean combat = owner.getTarget() != null && owner.getTarget().isAlive();
        var expression = owner.expressions.update(owner.level().getGameTime(), danger, combat,
                owner.hasActiveWorkOrder() || owner.isMiningAnimation() || owner.actionAnimation() != 0);
        owner.getEntityData().set(CompanionEntity.DATA_EXPRESSION, expression.ordinal());
        // A hunt for meat is no fight; a monster he fights or one in sight of either of them is.
        owner.getEntityData().set(CompanionEntity.DATA_FIGHTING, combat && owner.getTarget() instanceof Monster || owner.nearbySocialThreat);
        owner.getEntityData().set(CompanionEntity.DATA_SOCIAL_SAFE, !danger && !combat && !owner.nearbySocialThreat && owner.tickCount - owner.lastHurtTick > 200
                && (owner.getOwnerPlayer() == null || owner.getOwnerPlayer().getHealth() > 8 && owner.getOwnerPlayer().hurtTime == 0));
    }
    boolean sociallySafe() { return owner.getEntityData().get(CompanionEntity.DATA_SOCIAL_SAFE); }
    String socialObservation() { return owner.getEntityData().get(CompanionEntity.DATA_SOCIAL); }
    int socialSequence() { return owner.getEntityData().get(CompanionEntity.DATA_SOCIAL_SERIAL); }
    long socialTime() { return owner.getEntityData().get(CompanionEntity.DATA_SOCIAL_TIME); }
    void socialEvent(String fact) { owner.getEntityData().set(CompanionEntity.DATA_SOCIAL, fact); owner.getEntityData().set(CompanionEntity.DATA_SOCIAL_SERIAL, socialSequence() + 1); owner.getEntityData().set(CompanionEntity.DATA_SOCIAL_TIME, owner.level().getGameTime()); }

    void save(CompoundTag tag) {
        if (!initialized) return;
        CompoundTag social = new CompoundTag(); social.putLong("Next", nextSpeech);
        ListTag entries = new ListTag();
        seen.forEach((key, tick) -> { CompoundTag entry = new CompoundTag(); entry.putString("Key", key); entry.putLong("Tick", tick); entries.add(entry); });
        social.put("Seen", entries); tag.put("SocialCadence", social);
    }
    void load(CompoundTag tag) {
        seen.clear(); initialized = tag.contains("SocialCadence");
        if (!initialized) { nextSpeech = 0; return; }
        CompoundTag social = tag.getCompound("SocialCadence");
        long now = owner.level().getGameTime();
        nextSpeech = Math.max(now, Math.min(now + CompanionConversation.MAX_GAP, social.getLong("Next")));
        ListTag entries = social.getList("Seen", 10);
        for (int i = Math.max(0, entries.size() - 32); i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i); String key = entry.getString("Key");
            if (key.length() <= 80) seen.put(key, Math.min(now, entry.getLong("Tick")));
        }
    }
}

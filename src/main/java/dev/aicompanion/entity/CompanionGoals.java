package dev.aicompanion.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-owned, versioned outcome, saved with the canonical companion, never a saved navigation route.
 * Only stone_pickaxes_for_two is supported in schema 1. New goal kinds can select other bounded skills.
 * Delivery means the designated player actually picked up the uniquely marked toss, not a spoken promise.
 */
public final class CompanionGoals {
    public static final String KEY = "PersistentGoal";
    public static final String KIND = "stone_pickaxes_for_two";
    public static final String OP = "aicompanion:delivery";
    private static final String RECEIPTS = "aicompanion:delivery_receipts";
    public static final int MAX_ATTEMPTS = 12, MAX_FAILURES = 3;
    public enum State { ACTIVE, PAUSED_PLAYER, PAUSED_COMBAT, BLOCKED, COMPLETED, CANCELLED, UNSUPPORTED }
    private final CompanionEntity owner;
    private UUID id, playerId, operation, giftEntity;
    private State state;
    private String step = "reconcile", reason = "";
    private int attempts, failures, ownConfirmed;
    private boolean playerConfirmed;
    private long workId, pickupWait;
    private String skill = "";
    private String playerBehavior = "";
    private boolean resumeCome;
    private int safeTicks;
    private final List<CompoundTag> results = new ArrayList<>();

    CompanionGoals(CompanionEntity owner) { this.owner = owner; }
    public UUID id() { return id; }
    public UUID operation() { return operation; }
    public UUID giftEntity() { return giftEntity; }
    public State state() { return state; }
    public String reason() { return reason; }
    public int attempts() { return attempts; }
    public int failures() { return failures; }
    public int resultCount() { return results.size(); }
    public boolean outstanding() { return state != null && state != State.COMPLETED && state != State.CANCELLED; }
    public boolean ownsWork(long work) { return workId > 0 && workId == work && outstanding(); }

    public CompanionActionResult accept(ServerPlayer player) {
        if (outstanding()) return CompanionActionResult.ok(status());
        if (owner.hasActiveWorkOrder()) return CompanionActionResult.blocked("Сначала закончу текущее дело. Потом попроси кирки ещё раз.");
        id = UUID.randomUUID(); playerId = player.getUUID(); operation = giftEntity = null;
        state = State.ACTIVE; step = "reconcile"; reason = ""; attempts = failures = ownConfirmed = 0;
        workId = pickupWait = 0; skill = playerBehavior = ""; results.clear();
        playerConfirmed = playerHasPick(player);
        if (playerConfirmed) record("existing_player_item", 0, 1, null, null);
        owner.noteDeed("Принял цель: пригодная каменная кирка мне и тебе; твою отдам в руки.", true);
        return CompanionActionResult.accepted("Сделаю нам каменные кирки, твою принесу. Подходящие готовые кирки учту.");
    }

    public CompanionActionResult resume() {
        if (state == null) return CompanionActionResult.failed("У меня нет сохранённой цели. Попроси сделать нам каменные кирки.");
        if (state == State.COMPLETED || state == State.CANCELLED || state == State.UNSUPPORTED) return CompanionActionResult.ok(status());
        if (attempts >= MAX_ATTEMPTS || failures >= MAX_FAILURES) return CompanionActionResult.blocked("Лимит попыток исчерпан. " + reason);
        if (owner.hasActiveWorkOrder()) return CompanionActionResult.blocked("Сначала закончу текущую работу.");
        state = State.ACTIVE; reason = ""; step = "reconcile"; workId = 0; safeTicks = 0; pickupWait = 0;
        return CompanionActionResult.accepted("Продолжу наш заказ по тому, что уже сделано.");
    }

    /** Cancel only this outcome; an unrelated player order must keep running. */
    public void cancel() {
        if (!outstanding()) return;
        boolean owns = ownsWork(owner.activeWorkId());
        state = State.CANCELLED; reason = "Отменено игроком."; resumeCome = false;
        workId = 0; skill = "";
        if (owns) owner.stopGathering();
    }

    public String nextTask() {
        if (!outstanding()) return "";
        if (operation != null && !playerConfirmed) return "Дождаться, пока ты подберёшь выданную кирку";
        return countPicks() < neededPicks() ? "Подготовить материалы и сделать недостающие каменные кирки"
                : !playerConfirmed ? "Принести и передать тебе каменную кирку" : "Проверить готовые кирки";
    }

    /** Any explicit player order suspends the outcome; only stop/cancel is terminal. */
    public void playerOrder(String action) {
        if (!outstanding() || action.startsWith("goal_")) return;
        resumeCome = false;
        if (action.equals("stop")) {
            state = State.CANCELLED; reason = "Отменено игроком.";
        } else {
            state = State.PAUSED_PLAYER; reason = "Выполняю другую команду игрока. Скажи «продолжай работу».";
            playerBehavior = action;
        }
        workId = 0; skill = "";
        owner.stopGathering();
    }

    /** A come order interrupted by logout keeps trying to follow the owner; stay keeps standing. */
    void restorePlayerBehavior() {
        if (state != State.PAUSED_PLAYER) return;
        if (playerBehavior.equals("come") || playerBehavior.equals("follow")) owner.setFollowing(true);
        if (playerBehavior.equals("wait")) owner.setFollowing(false);
    }

    public String status() {
        if (state == null) return "Сохранённого заказа на кирки пока нет.";
        return switch (state) {
            case COMPLETED -> "Заказ на кирки выполнен: твою получил ты, одну пригодную я оставил себе.";
            case CANCELLED -> "Заказ на кирки отменён. Сам его не возобновлю.";
            case UNSUPPORTED -> "Заказ остановлен: " + reason;
            case PAUSED_PLAYER, BLOCKED -> "Заказ на кирки на паузе. " + reason;
            case PAUSED_COMBAT -> "Сначала разберусь с опасностью, потом продолжу заказ на кирки.";
            case ACTIVE -> operation != null && !playerConfirmed ? "Твою кирку бросил рядом с тобой; жду, пока ты её подберёшь."
                    : "Готовлю нам каменные кирки. У меня пригодных: " + countPicks() + "; твоя " + (playerConfirmed ? "уже есть." : "ещё не передана.");
        };
    }

    /** Every server AI tick. Actual skills still own movement, mining, recipes, loot and tool wear. */
    void tick() {
        if (state == State.PAUSED_PLAYER && resumeCome && owner.getOwnerPlayer() != null
                && !owner.hasActiveWorkOrder() && owner.canWorkNow()) {
            resumeCome = false;
            owner.startWork("come", "", 1);
        }
        if (!outstanding() || state == State.UNSUPPORTED || state == State.PAUSED_PLAYER || state == State.BLOCKED) return;
        ServerPlayer player = owner.getOwnerPlayer();
        if (player == null || !player.isAlive() || player.isSpectator()) return;
        if (!playerId.equals(player.getUUID()) || !playerId.equals(owner.getOwnerId())) { invalid("Владелец сохранённой цели не совпадает."); return; }
        if (danger(player)) {
            if (state != State.PAUSED_COMBAT) {
                var enemy = owner.getTarget();
                state = State.PAUSED_COMBAT; step = "reconcile"; reason = "Временный бой.";
                workId = 0; skill = ""; owner.stopGathering(); owner.setTarget(enemy);
            }
            safeTicks = 0; return;
        }
        if (state == State.PAUSED_COMBAT) {
            if (++safeTicks < 100) return;
            state = State.ACTIVE; reason = "";
        }
        // Persist receipts independently of a loaded companion; a picked-up marked item is also evidence after loading.
        if (!playerConfirmed && operation != null && (hasReceipt(player, operation) || hasMarkedPick(player, operation))) {
            playerConfirmed = true; record("pickup", 0, 1, operation, giftEntity);
        }
        if (workId > 0) {
            CompanionActionResult result = owner.workResult(workId);
            if (result == null) return;
            long finished = workId; workId = 0;
            if (result.evidence() != null) record(result.evidence().action(), finished, result.evidence().count(), result.evidence().operation(), result.evidence().itemEntity());
            // A failed skill can still have acquired the entire outcome. Reconcile facts before refusing.
            if (!result.success() && (skill.equals("give_items") && operation == null || countPicks() < neededPicks())) { failures++; block(result.message()); return; }
            skill = "";
        }
        if (owner.hasActiveWorkOrder() || owner.hasPendingLoot() || !owner.canWorkNow() || owner.isSelfRescuing() || owner.actionAnimation() != 0) return;
        ownConfirmed = countPicks();
        if (playerConfirmed && ownConfirmed >= 1) {
            state = State.COMPLETED; step = "done"; reason = "";
            owner.noteDeed("Цель выполнена: моя пригодная каменная кирка сохранена, кирка игрока подтверждена.", true);
            owner.autonomySay("Готово: твоя каменная кирка у тебя, моя осталась у меня."); return;
        }
        if (operation != null && !playerConfirmed) {
            step = "await_pickup";
            // Never mint a replacement for an issued item, even if its chunk is unloaded or it was destroyed.
            if (++pickupWait > 20 * 120) block("Твоя кирка уже выдана, но подбор не подтверждён. Подбери её и скажи «продолжай работу». Повторную не выдаю.");
            return;
        }
        // A suitable pickaxe acquired by the player before delivery also satisfies their share.
        if (!playerConfirmed && playerHasPick(player)) { playerConfirmed = true; record("existing_player_item", 0, 1, null, null); return; }
        if (attempts >= MAX_ATTEMPTS) { block("Достигнут предел безопасных попыток. " + reason); return; }
        if (ownConfirmed < neededPicks()) dispatch(player, "craft_item", neededPicks() - ownConfirmed);
        else dispatch(player, "give_items", 1);
    }

    private int neededPicks() { return playerConfirmed || operation != null ? 1 : 2; }
    /** Recalculated immediately before crafting: preparation may already have made a stone mining tool. */
    int craftRemainder(long work) { return ownsWork(work) && skill.equals("craft_item") ? Math.max(0, neededPicks() - countPicks()) : -1; }
    private void dispatch(ServerPlayer player, String action, int count) {
        attempts++; step = action; skill = action;
        CompanionActionResult result = CompanionEvents.executeGoal(player, action, "minecraft:stone_pickaxe", count);
        if (!result.success()) { failures++; block(result.message()); return; }
        workId = owner.activeWorkId();
        if (workId == 0) { failures++; block("Навык не подтвердил начало работы."); }
    }
    private void block(String why) {
        state = State.BLOCKED; reason = why; workId = 0; skill = "";
        owner.noteDeed("Цель на кирки приостановлена: " + why, false);
        owner.autonomySay("С кирками пока загвоздка. " + dev.aicompanion.CompanionFailureMessages.explain("craft_item", why));
    }
    private void invalid(String why) { state = State.UNSUPPORTED; reason = why; workId = 0; }
    private boolean danger(ServerPlayer player) {
        return owner.getTarget() != null && owner.getTarget().isAlive() || owner.isOnFire() || owner.isInLava()
                || owner.getHealth() <= 6 || player.hurtTime > 0;
    }
    static boolean usable(ItemStack stack) { return stack.is(Items.STONE_PICKAXE) && stack.getDamageValue() < stack.getMaxDamage(); }
    public int countPicks() {
        int n = 0;
        for (int i = 0; i < owner.getContainerSize(); i++) if (usable(owner.getItem(i))) n += owner.getItem(i).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (usable(owner.getItemBySlot(slot))) n += owner.getItemBySlot(slot).getCount();
        return n;
    }
    private static boolean playerHasPick(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (usable(player.getInventory().getItem(i))) return true;
        return false;
    }
    private static boolean hasMarkedPick(ServerPlayer player, UUID op) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (usable(stack) && op.equals(mark(stack))) return true;
        }
        return false;
    }
    private static UUID mark(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.hasUUID(OP) ? data.getUUID(OP) : null;
    }
    /** Invoked by the existing physical toss, on the same server tick as removal from Oleg's bag. */
    UUID prepareGift(long work, ItemStack stack) {
        if (!ownsWork(work) || !skill.equals("give_items") || !usable(stack) || operation != null) return null;
        UUID op = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> { tag.putUUID(OP, op); tag.putUUID("Goal", id); tag.putUUID("Recipient", playerId); });
        return op;
    }
    void giftSpawned(UUID op, UUID entity) {
        if (op == null) return;
        operation = op; giftEntity = entity; pickupWait = 0; step = "await_pickup";
        record("toss", workId, 1, op, entity);
    }
    public static void pickedUp(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ItemStack original = event.getOriginalStack(); UUID op = mark(original);
        if (op == null || !usable(original) || !event.getCurrentStack().isEmpty() || !hasMarkedPick(player, op)) return;
        CompoundTag data = original.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.hasUUID("Recipient") || !data.getUUID("Recipient").equals(player.getUUID())) return;
        var receipts = player.getPersistentData().getCompound(RECEIPTS);
        receipts.putBoolean(op.toString(), true); player.getPersistentData().put(RECEIPTS, receipts);
        var companion = CompanionEvents.getOwnedCompanion(player);
        if (companion != null && op.equals(companion.goals().operation)) {
            companion.goals().playerConfirmed = true;
            companion.goals().record("pickup", 0, 1, op, event.getItemEntity().getUUID());
        }
    }
    private static boolean hasReceipt(ServerPlayer player, UUID op) { return player.getPersistentData().getCompound(RECEIPTS).getBoolean(op.toString()); }
    private void record(String action, long work, int count, UUID op, UUID item) {
        if (results.stream().anyMatch(t -> work > 0 && t.getLong("WorkId") == work && t.getString("Action").equals(action)
                || op != null && t.hasUUID("Operation") && op.equals(t.getUUID("Operation")) && t.getString("Action").equals(action))) return;
        CompoundTag t = new CompoundTag(); t.putString("Action", action); t.putLong("WorkId", work); t.putString("Item", "minecraft:stone_pickaxe");
        t.putInt("Count", count); if (playerId != null) t.putUUID("Recipient", action.equals("craft_item") ? owner.getUUID() : playerId);
        if (op != null) t.putUUID("Operation", op); if (item != null) t.putUUID("ItemEntity", item);
        results.add(t); if (results.size() > 24) results.removeFirst();
    }
    void skillCompleted(CompanionActionResult.Evidence facts) {
        if (facts != null && ownsWork(facts.workId())) record(facts.action(), facts.workId(), facts.count(), facts.operation(), facts.itemEntity());
    }
    public void save(CompoundTag root) {
        if (state == null) return;
        CompoundTag t = new CompoundTag(); t.putInt("Schema", 1); t.putString("Kind", KIND);
        if (id != null) t.putUUID("Id", id); if (playerId != null) t.putUUID("Owner", playerId);
        t.putString("DesiredItem", "minecraft:stone_pickaxe"); t.putInt("OlegCount", 1); t.putInt("PlayerCount", 1);
        t.putUUID("CompanionRecipient", owner.getUUID()); t.putString("PlayerBehavior", playerBehavior);
        t.putBoolean("ResumeCome", resumeCome);
        t.putString("State", state.name()); t.putString("Step", step); t.putString("Reason", reason);
        t.putInt("Attempts", attempts); t.putInt("Failures", failures); t.putInt("AttemptLimit", MAX_ATTEMPTS);
        t.putInt("OwnConfirmed", ownConfirmed); t.putBoolean("PlayerConfirmed", playerConfirmed);
        t.putLong("WorkId", workId); t.putString("Skill", skill); t.putLong("PickupWait", pickupWait);
        if (operation != null) t.putUUID("Operation", operation); if (giftEntity != null) t.putUUID("GiftEntity", giftEntity);
        ListTag list = new ListTag(); for (CompoundTag r : results) list.add(r.copy()); t.put("Results", list); root.put(KEY, t);
    }
    public void load(CompoundTag root) {
        state = null; id = playerId = operation = giftEntity = null; workId = 0; skill = playerBehavior = ""; results.clear(); safeTicks = 0; resumeCome = false;
        if (!root.contains(KEY)) return; // Old worlds have no goal and behave exactly as before.
        try {
            CompoundTag t = root.getCompound(KEY);
            if (t.getInt("Schema") != 1 || !KIND.equals(t.getString("Kind")) || !t.hasUUID("Id") || !t.hasUUID("Owner")
                    || !"minecraft:stone_pickaxe".equals(t.getString("DesiredItem")) || t.getInt("OlegCount") != 1 || t.getInt("PlayerCount") != 1
                    || !t.contains("Attempts", Tag.TAG_INT) || !t.contains("Failures", Tag.TAG_INT) || t.getInt("AttemptLimit") != MAX_ATTEMPTS
                    || !t.contains("PlayerConfirmed", Tag.TAG_BYTE) || !t.contains("State", Tag.TAG_STRING)
                    || !t.contains("Results", Tag.TAG_LIST) || !t.contains("PlayerBehavior", Tag.TAG_STRING))
                throw new IllegalArgumentException("Неизвестный формат или результат цели.");
            id = t.getUUID("Id"); playerId = t.getUUID("Owner"); state = State.valueOf(t.getString("State"));
            reason = t.getString("Reason"); step = "reconcile"; attempts = t.getInt("Attempts"); failures = t.getInt("Failures");
            playerBehavior = t.getString("PlayerBehavior");
            resumeCome = state == State.PAUSED_PLAYER && playerBehavior.equals("come") && (root.getBoolean("WorkActive") || t.getBoolean("ResumeCome"));
            if (attempts < 0 || attempts > MAX_ATTEMPTS || failures < 0 || failures > MAX_FAILURES || !playerId.equals(owner.getOwnerId()))
                throw new IllegalArgumentException("Повреждены владелец или пределы попыток.");
            ownConfirmed = 0; playerConfirmed = t.getBoolean("PlayerConfirmed"); pickupWait = Math.max(0, Math.min(2401, t.getLong("PickupWait")));
            if (t.contains("Operation") != t.hasUUID("Operation") || t.contains("GiftEntity") != t.hasUUID("GiftEntity")
                    || t.hasUUID("Operation") != t.hasUUID("GiftEntity")) throw new IllegalArgumentException("Неполная запись передачи; повторять её небезопасно.");
            operation = t.hasUUID("Operation") ? t.getUUID("Operation") : null; giftEntity = t.hasUUID("GiftEntity") ? t.getUUID("GiftEntity") : null;
            ListTag list = t.getList("Results", Tag.TAG_COMPOUND); for (int i = Math.max(0, list.size() - 24); i < list.size(); i++) results.add(list.getCompound(i).copy());
            if (operation == null && results.stream().anyMatch(r -> r.getString("Action").equals("toss"))) throw new IllegalArgumentException("У выданного предмета потерян номер операции.");
            // WorkId/Skill/old routes are diagnostic only. The resumed skill is chosen from actual inventory and receipts.
        } catch (RuntimeException ex) { invalid("Не могу безопасно прочитать цель: " + ex.getMessage()); }
    }
}

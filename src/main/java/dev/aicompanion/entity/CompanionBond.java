package dev.aicompanion.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * How the companion feels about one player. The score only changes the tone of speech, never what the companion does.
 * Every input is a real deed; time arrives as parameters (game day, game tick) so the rules are testable without a world.
 */
public final class CompanionBond {
    public static final int BASE = 20;
    public static final int MIN = -100;
    public static final int MAX = 100;
    public static final long DAY_TICKS = 24000L;
    public static final int GIFT_DAILY_CAP = 15;
    public static final int DEED_DAILY_CAP = 5;
    public static final int HIT_DAILY_CAP = 12;
    public static final int RESCUE_POINTS = 4;
    public static final int DEED_POINTS = 1;
    public static final int HIT_POINTS = 6;
    public static final int ABANDON_POINTS = 3;
    public static final long HIT_GAP_TICKS = 600L;
    public static final long ABANDON_AFTER_TICKS = 24000L;
    public static final int MAX_KINDS = 64;
    public static final int MAX_REASONS = 3;
    private static final int MAX_DECAY_DAYS = 400;
    private static final int FOOD_PIECES = 8;
    private static final int MATERIAL_PIECES = 16;
    private static final Pattern METAL = Pattern.compile("(^|_)(iron|gold|golden)(_|$)");

    public enum Tier {
        OFFENDED("обижен", MIN), WARY("настороже", 0), ACQUAINTANCE("знакомый", 15),
        PAL("приятель", 40), BUDDY("кореш", 65), BEST_FRIEND("лучший друг", 85);

        private final String label;
        private final int from;

        Tier(String label, int from) { this.label = label; this.from = from; }

        public String label() { return label; }

        public static Tier of(int score) {
            Tier found = OFFENDED;
            for (Tier tier : values()) if (score >= tier.from) found = tier;
            return found;
        }
    }

    /** How valuable a kind of item is as a gift; {@link #points} says what a given growth of the balance is worth. */
    public enum GiftClass {
        MATERIAL, FOOD, METAL, PRECIOUS;

        public int points(int growth) {
            if (growth <= 0) return 0;
            return switch (this) {
                case MATERIAL -> growth >= MATERIAL_PIECES ? 3 : 0;
                case FOOD -> growth >= FOOD_PIECES ? 3 : 0;
                case METAL -> 5;
                case PRECIOUS -> 8;
            };
        }
    }

    public enum Reason {
        GIFT("подарок"), RESCUE("спас от врага"), DEED("общее дело"), HIT("ударил"), ABANDONED("бросил одного надолго");

        private final String label;

        Reason(String label) { this.label = label; }

        public String label() { return label; }
    }

    /** What happened to one kind of item while the inventory was open: positive = put in, negative = taken out. */
    public record ItemChange(String kind, int delta, GiftClass giftClass) { }

    private final Map<String, int[]> ledger = new LinkedHashMap<>(); // kind -> {balance, peak}, least recently touched first
    private final List<Reason> reasons = new ArrayList<>();
    private int score = BASE;
    private long day = -1;
    private int giftToday;
    private int deedToday;
    private int hitToday;
    private boolean abandonedToday;
    private boolean hasHit;
    private long lastHit;

    public static GiftClass classify(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (stack.isEnchanted() || stack.getRarity().ordinal() >= Rarity.RARE.ordinal() || path.contains("diamond") || path.contains("netherite")) return GiftClass.PRECIOUS;
        if (METAL.matcher(path).find()) return GiftClass.METAL;
        return stack.has(DataComponents.FOOD) ? GiftClass.FOOD : GiftClass.MATERIAL;
    }

    /** The kind a stack is counted under: its item id, set apart when enchanted. */
    public static String kindOf(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return stack.isEnchanted() ? id + "#enchanted" : id;
    }

    public static ItemChange change(ItemStack sample, int delta) { return new ItemChange(kindOf(sample), delta, classify(sample)); }

    public static long dayOf(long gameTime) { return Math.floorDiv(gameTime, DAY_TICKS); }

    /** Starts a new day: daily counters reset and the score drifts one point per elapsed day toward the base. */
    public void advanceTo(long today) {
        if (day < 0 || today < day) { day = Math.max(0, today); resetDaily(); return; }
        if (today == day) return;
        int steps = (int) Math.min(today - day, MAX_DECAY_DAYS);
        score += Integer.signum(BASE - score) * Math.min(steps, Math.abs(BASE - score));
        day = today;
        resetDaily();
    }

    private void resetDaily() { giftToday = 0; deedToday = 0; hitToday = 0; abandonedToday = false; }

    /**
     * Credits only growth of the cumulative per-kind balance (put minus taken, never below zero) above its previous peak,
     * so putting things in, taking them back and putting them in again earns once. One closed inventory pays its highest class.
     */
    public int gift(long today, List<ItemChange> changes) {
        advanceTo(today);
        int best = 0;
        for (ItemChange item : changes) {
            if (item.delta() == 0 || item.kind() == null || item.kind().isEmpty()) continue;
            int[] entry = ledger.remove(item.kind());
            if (entry == null) entry = new int[2];
            entry[0] = Math.max(0, entry[0] + item.delta());
            int growth = Math.max(0, entry[0] - entry[1]);
            entry[1] += growth;
            ledger.put(item.kind(), entry);
            best = Math.max(best, item.giftClass().points(growth));
        }
        while (ledger.size() > MAX_KINDS) ledger.remove(ledger.keySet().iterator().next());
        int granted = Math.max(0, Math.min(best, GIFT_DAILY_CAP - giftToday));
        giftToday += granted;
        return move(granted, Reason.GIFT);
    }

    public int rescued(long today) {
        advanceTo(today);
        return move(RESCUE_POINTS, Reason.RESCUE);
    }

    public int sharedDeed(long today) {
        advanceTo(today);
        if (deedToday + DEED_POINTS > DEED_DAILY_CAP) return 0;
        deedToday += DEED_POINTS;
        return move(DEED_POINTS, Reason.DEED);
    }

    /** Not during a fight with a monster nearby (the player may be swinging at it), at most once per 30 s and 12 points a day. */
    public int hitByPlayer(long today, long gameTime, boolean fightingMonster) {
        advanceTo(today);
        if (fightingMonster || hasHit && gameTime - lastHit < HIT_GAP_TICKS || hitToday + HIT_POINTS > HIT_DAILY_CAP) return 0;
        hasHit = true;
        lastHit = gameTime;
        hitToday += HIT_POINTS;
        return move(-HIT_POINTS, Reason.HIT);
    }

    /** The player left the companion idle far away for longer than 20 minutes; counted once per game day. */
    public int leftIdleFar(long today, long idleTicks) {
        advanceTo(today);
        if (idleTicks <= ABANDON_AFTER_TICKS || abandonedToday) return 0;
        abandonedToday = true;
        return move(-ABANDON_POINTS, Reason.ABANDONED);
    }

    private int move(int delta, Reason reason) {
        int before = score;
        score = Math.max(MIN, Math.min(MAX, score + delta));
        if (delta != 0) {
            reasons.remove(reason);
            reasons.add(reason);
            while (reasons.size() > MAX_REASONS) reasons.removeFirst();
        }
        return score - before;
    }

    public int score() { return score; }

    public Tier tier() { return Tier.of(score); }

    /** Oldest first, at most three, each reason once. */
    public List<Reason> reasons() { return List.copyOf(reasons); }

    public int ledgerKinds() { return ledger.size(); }

    public int balance(String kind) { return ledger.containsKey(kind) ? ledger.get(kind)[0] : 0; }

    public int peak(String kind) { return ledger.containsKey(kind) ? ledger.get(kind)[1] : 0; }

    public CompanionBond copy() { return load(save()); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Score", score); tag.putLong("Day", day);
        tag.putInt("GiftToday", giftToday); tag.putInt("DeedToday", deedToday); tag.putInt("HitToday", hitToday);
        tag.putBoolean("AbandonedToday", abandonedToday);
        if (hasHit) tag.putLong("LastHit", lastHit);
        ListTag names = new ListTag();
        for (Reason reason : reasons) names.add(StringTag.valueOf(reason.name()));
        tag.put("Reasons", names);
        ListTag kinds = new ListTag();
        for (var entry : ledger.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("Kind", entry.getKey()); item.putInt("Balance", entry.getValue()[0]); item.putInt("Peak", entry.getValue()[1]);
            kinds.add(item);
        }
        tag.put("Ledger", kinds);
        return tag;
    }

    public static CompanionBond load(CompoundTag tag) {
        CompanionBond bond = new CompanionBond();
        if (tag.contains("Score")) bond.score = Math.max(MIN, Math.min(MAX, tag.getInt("Score")));
        bond.day = tag.contains("Day") ? tag.getLong("Day") : -1;
        bond.giftToday = Math.max(0, tag.getInt("GiftToday")); bond.deedToday = Math.max(0, tag.getInt("DeedToday")); bond.hitToday = Math.max(0, tag.getInt("HitToday"));
        bond.abandonedToday = tag.getBoolean("AbandonedToday");
        bond.hasHit = tag.contains("LastHit");
        bond.lastHit = tag.getLong("LastHit");
        for (Tag name : tag.getList("Reasons", Tag.TAG_STRING)) {
            for (Reason reason : Reason.values()) if (reason.name().equals(name.getAsString()) && !bond.reasons.contains(reason)) bond.reasons.add(reason);
        }
        while (bond.reasons.size() > MAX_REASONS) bond.reasons.removeFirst();
        ListTag kinds = tag.getList("Ledger", Tag.TAG_COMPOUND);
        for (int i = 0; i < kinds.size(); i++) {
            CompoundTag item = kinds.getCompound(i);
            String kind = item.getString("Kind");
            if (kind.isEmpty() || kind.length() > 128) continue;
            int balance = Math.max(0, item.getInt("Balance"));
            bond.ledger.put(kind, new int[] {balance, Math.max(balance, item.getInt("Peak"))});
        }
        while (bond.ledger.size() > MAX_KINDS) bond.ledger.remove(bond.ledger.keySet().iterator().next());
        return bond;
    }
}

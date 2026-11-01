package dev.aicompanion.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * What Oleg has right now, read on the server thread. A long goal reconciles with this, never with what it did before:
 * after a reload, a death or an interruption the next step is chosen from the bag, the armour and the world as they are.
 */
public final class CompanionFacts {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /**
     * How far from where he stands iron counts as near, for what is optional: further than this and the goal goes on without it. At most half of
     * his search range, so that the wood for the same shield, fetched first, can take him off and the ore is still inside the range.
     */
    private static final int IRON_IN_SIGHT_RADIUS = 24;
    private final Map<Item, Integer> carried;
    private final Map<EquipmentSlot, Item> worn;
    private final boolean dragonDead;
    private final int mineOpened;
    private final boolean belowMine;
    private final BooleanSupplier ironInSight;
    private final boolean shieldTrekSpent;

    private CompanionFacts(Map<Item, Integer> carried, Map<EquipmentSlot, Item> worn, boolean dragonDead, int mineOpened, boolean belowMine,
                           BooleanSupplier ironInSight, boolean shieldTrekSpent) {
        this.carried = Map.copyOf(carried); this.worn = Map.copyOf(worn); this.dragonDead = dragonDead; this.mineOpened = mineOpened; this.belowMine = belowMine;
        this.ironInSight = ironInSight; this.shieldTrekSpent = shieldTrekSpent;
    }

    public static CompanionFacts of(CompanionEntity c) {
        Map<Item, Integer> carried = new HashMap<>();
        for (int i = 0; i < c.getContainerSize(); i++) add(carried, c.getItem(i));
        add(carried, c.getItemBySlot(EquipmentSlot.MAINHAND));
        add(carried, c.getItemBySlot(EquipmentSlot.OFFHAND));
        Map<EquipmentSlot, Item> worn = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : ARMOR) if (usable(c.getItemBySlot(slot))) worn.put(slot, c.getItemBySlot(slot).getItem());
        if (usable(c.getOffhandItem())) worn.put(EquipmentSlot.OFFHAND, c.getOffhandItem().getItem());
        var end = c.getServer() == null ? null : c.getServer().getLevel(Level.END);
        boolean dragonDead = end != null && end.getDragonFight() != null && end.getDragonFight().hasPreviouslyKilledDragon();
        boolean belowMine = c.mineSite().exists() && (c.getY() < c.mineSite().entrance().getY() - CompanionExpedition.UNDERGROUND_DEPTH || c.mineSite().buried() && !CompanionExpeditionRecord.underSky(c.level(), c.blockPosition().above()));
        return new CompanionFacts(carried, worn, dragonDead, c.mineSite().opened(), belowMine, ironInSight(c), false);
    }

    /** Looked up once, and only if a plan asks: the world is scanned around where he stands now. */
    private static BooleanSupplier ironInSight(CompanionEntity c) {
        Boolean[] seen = {null};
        return () -> {
            if (seen[0] == null) seen[0] = c.level() instanceof ServerLevel level
                    && new CompanionOre.Search(level, c.blockPosition(), Math.min(IRON_IN_SIGHT_RADIUS, c.getSearchRadius() / 2), CompanionOre.Kind.IRON).anyInSight(level);
            return seen[0];
        };
    }

    /** The same facts, told whether the goal has already gone out for the iron of a shield (it goes once). */
    public CompanionFacts withShieldTrekSpent(boolean spent) {
        return new CompanionFacts(carried, worn, dragonDead, mineOpened, belowMine, ironInSight, spent);
    }

    /** How many of the item he carries in the bag and in his hands; armour he wears is not counted here. */
    public int carried(Item item) { return carried.getOrDefault(item, 0); }

    public int carried(Predicate<Item> kind) {
        return carried.entrySet().stream().filter(entry -> kind.test(entry.getKey())).mapToInt(Map.Entry::getValue).sum();
    }

    /** He stands down in his mine, say after a reload: what is made on the surface waits for the next time he is up. */
    public boolean belowMine() { return belowMine; }

    /** Iron ore in the open within a short walk of where he stands; ore inside the rock does not count, he does not see through stone. */
    public boolean ironInSight() { return ironInSight.getAsBoolean(); }

    /** The goal has already gone out for the iron of a shield once. */
    public boolean shieldTrekSpent() { return shieldTrekSpent; }

    /** The armour piece in the slot (or what the left hand holds), or null. */
    public Item worn(EquipmentSlot slot) { return worn.get(slot); }

    public boolean dragonDead() { return dragonDead; }

    /** Cells his diamond mine has opened (stairs and tunnels): what a step of the expedition leaves behind even without a diamond. */
    public int mineOpened() { return mineOpened; }

    private static void add(Map<Item, Integer> carried, ItemStack stack) {
        if (usable(stack)) carried.merge(stack.getItem(), stack.getCount(), Integer::sum);
    }

    /** A broken tool or a worn-out piece is not something he has. */
    static boolean usable(ItemStack stack) {
        return !stack.isEmpty() && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage());
    }
}

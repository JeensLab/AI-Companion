package dev.aicompanion.entity;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What Oleg has right now, read on the server thread. A long goal reconciles with this, never with what it did before:
 * after a reload, a death or an interruption the next step is chosen from the bag, the armour and the world as they are.
 */
public final class CompanionFacts {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final Map<Item, Integer> carried;
    private final Map<EquipmentSlot, Item> worn;
    private final boolean dragonDead;
    /** Looked up when asked (a goal with a portal stage), not every second for any goal. */
    private final CompanionEntity c;
    private Boolean portal;

    private CompanionFacts(Map<Item, Integer> carried, Map<EquipmentSlot, Item> worn, boolean dragonDead, CompanionEntity c) {
        this.carried = Map.copyOf(carried); this.worn = Map.copyOf(worn); this.dragonDead = dragonDead; this.c = c;
    }

    public static CompanionFacts of(CompanionEntity c) {
        Map<Item, Integer> carried = new HashMap<>();
        for (int i = 0; i < c.getContainerSize(); i++) add(carried, c.getItem(i));
        add(carried, c.getItemBySlot(EquipmentSlot.MAINHAND));
        add(carried, c.getItemBySlot(EquipmentSlot.OFFHAND));
        Map<EquipmentSlot, Item> worn = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : ARMOR) if (usable(c.getItemBySlot(slot))) worn.put(slot, c.getItemBySlot(slot).getItem());
        var end = c.getServer() == null ? null : c.getServer().getLevel(Level.END);
        boolean dragonDead = end != null && end.getDragonFight() != null && end.getDragonFight().hasPreviouslyKilledDragon();
        return new CompanionFacts(carried, worn, dragonDead, c);
    }

    /** How many of the item he carries in the bag and in his hands; armour he wears is not counted here. */
    public int carried(Item item) { return carried.getOrDefault(item, 0); }

    public int carried(Predicate<Item> kind) {
        return carried.entrySet().stream().filter(entry -> kind.test(entry.getKey())).mapToInt(Map.Entry::getValue).sum();
    }

    /** The armour piece in the slot, or null. */
    public Item worn(EquipmentSlot slot) { return worn.get(slot); }

    public boolean dragonDead() { return dragonDead; }

    /** A lit Nether portal he knows in this world, wherever the player is: one he built or saw, its block still a portal (decided 09.11). */
    public boolean portal() {
        if (portal == null) portal = c.level() instanceof net.minecraft.server.level.ServerLevel level
                && CompanionPortalBuild.known(c, level, CompanionPortalBuild.center(c), Integer.MAX_VALUE) != null;
        return portal;
    }

    private static void add(Map<Item, Integer> carried, ItemStack stack) {
        if (usable(stack)) carried.merge(stack.getItem(), stack.getCount(), Integer::sum);
    }

    /** A broken tool or a worn-out piece is not something he has. */
    static boolean usable(ItemStack stack) {
        return !stack.isEmpty() && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage());
    }
}

package dev.aicompanion.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.BlockPos;
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
    private static final int IRON_IN_SIGHT_RADIUS = 24, OWN_WAY_SQR = 16;
    private final Map<Item, Integer> carried;
    private final Map<EquipmentSlot, Item> worn;
    private final boolean dragonDead;
    private final int mineOpened;
    private final boolean belowMine;
    private final boolean onOwnWay;
    private final BooleanSupplier ironInSight, coalInSight;
    private final boolean shieldTrekSpent;

    private CompanionFacts(Map<Item, Integer> carried, Map<EquipmentSlot, Item> worn, boolean dragonDead, int mineOpened, boolean belowMine,
                           boolean onOwnWay, BooleanSupplier ironInSight, BooleanSupplier coalInSight, boolean shieldTrekSpent) {
        this.carried = Map.copyOf(carried); this.worn = Map.copyOf(worn); this.dragonDead = dragonDead; this.mineOpened = mineOpened; this.belowMine = belowMine; this.onOwnWay = onOwnWay;
        this.ironInSight = ironInSight; this.coalInSight = coalInSight; this.shieldTrekSpent = shieldTrekSpent;
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
        // The record of the mine keeps no dimension, and a mine is dug in the Overworld only.
        boolean belowMine = c.level().dimension() == Level.OVERWORLD && c.mineSite().exists() && !CompanionExpeditionRecord.underSky(c.level(), c.blockPosition().above())
                && (c.getY() < c.mineSite().entrance().getY() - CompanionExpedition.UNDERGROUND_DEPTH || c.mineSite().buried());
        BlockPos here = c.blockPosition();
        var site = c.mineSite();
        boolean onOwnWay = belowMine && (site.buried() || site.stands().isEmpty() || site.stands().stream().anyMatch(stand -> stand.distSqr(here) <= OWN_WAY_SQR)
                || site.above().stream().anyMatch(cell -> cell.distSqr(here) <= OWN_WAY_SQR) || site.inTunnel(here));
        return new CompanionFacts(carried, worn, dragonDead, site.openedTotal(), belowMine, onOwnWay, inSight(c, CompanionOre.Kind.IRON), coalSearch(c), false);
    }

    /** Looked up once, and only if a plan asks: the world is scanned around where he stands now. */
    private static BooleanSupplier inSight(CompanionEntity c, CompanionOre.Kind kind) {
        Boolean[] seen = {null};
        return () -> {
            if (seen[0] == null) seen[0] = c.level() instanceof ServerLevel level
                    && new CompanionOre.Search(level, c.blockPosition(), Math.min(IRON_IN_SIGHT_RADIUS, c.getSearchRadius() / 2), kind).anyInSight(level);
            return seen[0];
        };
    }

    /** Coal anywhere the coal step itself would look (its search radius and depth), buried or not: the full torch stock is asked for only when that step can find some. */
    private static BooleanSupplier coalSearch(CompanionEntity c) {
        Boolean[] seen = {null};
        return () -> {
            if (seen[0] == null) {
                if (!(c.level() instanceof ServerLevel level)) return false;
                var search = new CompanionOre.Search(level, c.blockPosition(), c.getSearchRadius(), CompanionOre.Kind.COAL);
                while (!search.scan(level)) { /* the sections are few, the search is done in one go */ }
                seen[0] = !search.results(level).isEmpty();
            }
            return seen[0];
        };
    }

    /** The same facts, told whether the goal has already gone out for the iron of a shield (it goes once). */
    public CompanionFacts withShieldTrekSpent(boolean spent) {
        return new CompanionFacts(carried, worn, dragonDead, mineOpened, belowMine, onOwnWay, ironInSight, coalInSight, spent);
    }

    /** How many of the item he carries in the bag and in his hands; armour he wears is not counted here. */
    public int carried(Item item) { return carried.getOrDefault(item, 0); }

    public int carried(Predicate<Item> kind) {
        return carried.entrySet().stream().filter(entry -> kind.test(entry.getKey())).mapToInt(Map.Entry::getValue).sum();
    }

    /** He stands down in his mine, say after a reload: what is made on the surface waits for the next time he is up. */
    public boolean belowMine() { return belowMine; }

    /** Below the entrance and on his own stairs or tunnels (not in a pit he fell into beside them): the surface kit really waits for the next time he is up. */
    public boolean inMine() { return onOwnWay; }

    /** Iron ore in the open within a short walk of where he stands; ore inside the rock does not count, he does not see through stone. */
    public boolean ironInSight() { return ironInSight.getAsBoolean(); }

    /** Coal within the coal step's own search, buried or not: the torches are topped up to the full stock only when it is there to be had. */
    public boolean coalInSight() { return coalInSight.getAsBoolean(); }

    /** The goal has already gone out for the iron of a shield once. */
    public boolean shieldTrekSpent() { return shieldTrekSpent; }

    /** The armour piece in the slot (or what the left hand holds), or null. */
    public Item worn(EquipmentSlot slot) { return worn.get(slot); }

    public boolean dragonDead() { return dragonDead; }

    /** Cells his diamond mine has opened since it began (stairs and tunnels, none counted twice for a cut-back): what a step of the expedition leaves behind even without a diamond. */
    public int mineOpened() { return mineOpened; }

    private static void add(Map<Item, Integer> carried, ItemStack stack) {
        if (usable(stack)) carried.merge(stack.getItem(), stack.getCount(), Integer::sum);
    }

    /** A broken tool or a worn-out piece is not something he has. */
    static boolean usable(ItemStack stack) {
        return !stack.isEmpty() && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage());
    }
}

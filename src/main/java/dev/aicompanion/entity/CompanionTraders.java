package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the companion knows about the traders he has talked to: who, where, and the real offers read on the server. Saved with the
 * entity and carried to the one who comes back; {@link #text} is the compact line the model reads.
 */
public final class CompanionTraders {
    /** Older sightings are pushed out beyond this many traders. */
    public static final int MAX = 30;
    public static final String WANDERING = "wandering_trader";

    /** One side of a deal; a book or a tool also carries its first enchantment (id and level), empty if none. */
    public record Stack(String item, int count, String enchantment, int level) { }
    /** What is paid (one or two stacks, {@code give2} null for one), what is got, and how many times it can still be made. */
    public record Deal(Stack give, Stack give2, Stack get, int left) { }
    /** One trader: the profession key (or {@link #WANDERING}), his level, where he was seen and when, and his deals. */
    public record Entry(UUID id, String kind, int level, BlockPos pos, String dimension, boolean fresh, long seen, List<Deal> deals) { }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public Collection<Entry> entries() { return List.copyOf(entries.values()); }
    public Entry entry(UUID id) { return entries.get(id); }
    public void forget(UUID id) { entries.remove(id); }

    /** Reads the trader's offers (the game rolls them for a new villager, as when a player opens the window) and keeps them. */
    public Entry remember(AbstractVillager trader, ServerLevel level) {
        List<Deal> deals = new ArrayList<>();
        for (MerchantOffer offer : trader.getOffers()) {
            ItemStack second = offer.getCostB();
            deals.add(new Deal(stack(offer.getCostA()), second.isEmpty() ? null : stack(second), stack(offer.getResult()), offer.getMaxUses() - offer.getUses()));
        }
        boolean villager = trader instanceof Villager;
        String kind = villager ? BuiltInRegistries.VILLAGER_PROFESSION.getKey(((Villager) trader).getVillagerData().getProfession()).getPath() : WANDERING;
        int level1 = villager ? ((Villager) trader).getVillagerData().getLevel() : 1;
        // Fresh: never traded, so the first deal made fixes his profession and goods (a wandering trader never is).
        boolean fresh = villager && ((Villager) trader).getVillagerXp() == 0 && level1 <= 1;
        Entry entry = new Entry(trader.getUUID(), kind, level1, trader.blockPosition(), level.dimension().location().toString(), fresh, level.getGameTime(), List.copyOf(deals));
        entries.remove(trader.getUUID());
        entries.put(trader.getUUID(), entry);
        while (entries.size() > MAX) entries.values().stream().min((a, b) -> Long.compare(a.seen(), b.seen())).ifPresent(oldest -> entries.remove(oldest.id()));
        return entry;
    }

    /**
     * Where a round at a remembered village is made, of the traders remembered {@code remembered} (one dimension) and the one nearest to
     * him: of the traders remembered around that one, the one from whom the farthest is nearest, so the round's 64 blocks take in the
     * village and not only its near edge (game 18.11: walked to the edge, he asked 2 of 5).
     */
    public static BlockPos village(List<BlockPos> remembered, BlockPos nearest) {
        // A village is some 100 blocks across: its traders are those within twice a round's reach of the nearest one.
        int across = 128;
        List<BlockPos> group = remembered.stream().filter(pos -> pos.distSqr(nearest) <= (double) across * across).toList();
        // The spot the farthest of them is nearest from; a trader's own spot, not a bare middle, which may be a roof or a hole.
        return group.stream().min(java.util.Comparator.comparingDouble(spot -> group.stream().mapToDouble(other -> other.distSqr(spot)).max().orElse(0)))
                .orElse(nearest);
    }

    static final String KEY = "Traders";
    /**
     * The line rides with every trade message the player writes, so it is held to some 4000 tokens (under the synced string's
     * 32767 too): a village of eight traders is about a fifth of it; a hall of 30 masters is cut, the most recently seen first.
     */
    static final int MAX_TEXT = 12000;

    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", e.id()); t.putString("Kind", e.kind()); t.putInt("Level", e.level()); t.putLong("Pos", e.pos().asLong());
            t.putString("Dim", e.dimension()); t.putBoolean("Fresh", e.fresh()); t.putLong("Seen", e.seen());
            ListTag deals = new ListTag();
            for (Deal d : e.deals()) {
                CompoundTag dt = new CompoundTag();
                dt.put("Give", saveStack(d.give())); if (d.give2() != null) dt.put("Give2", saveStack(d.give2()));
                dt.put("Get", saveStack(d.get())); dt.putInt("Left", d.left());
                deals.add(dt);
            }
            t.put("Deals", deals);
            list.add(t);
        }
        tag.put(KEY, list);
    }

    /** An old save has no key: the memory is empty. */
    void load(CompoundTag tag) {
        entries.clear();
        ListTag list = tag.getList(KEY, 10);
        for (int i = 0; i < Math.min(MAX, list.size()); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("Id")) continue;
            List<Deal> deals = new ArrayList<>();
            ListTag dl = t.getList("Deals", 10);
            for (int j = 0; j < Math.min(32, dl.size()); j++) {
                CompoundTag dt = dl.getCompound(j);
                deals.add(new Deal(loadStack(dt.getCompound("Give")), dt.contains("Give2", 10) ? loadStack(dt.getCompound("Give2")) : null, loadStack(dt.getCompound("Get")), dt.getInt("Left")));
            }
            entries.put(t.getUUID("Id"), new Entry(t.getUUID("Id"), t.getString("Kind"), t.getInt("Level"), BlockPos.of(t.getLong("Pos")), t.getString("Dim"), t.getBoolean("Fresh"), t.getLong("Seen"), List.copyOf(deals)));
        }
    }

    private static CompoundTag saveStack(Stack s) {
        CompoundTag t = new CompoundTag();
        t.putString("Item", s.item()); t.putInt("Count", s.count()); t.putString("Ench", s.enchantment()); t.putInt("Lvl", s.level());
        return t;
    }

    private static Stack loadStack(CompoundTag t) { return new Stack(t.getString("Item"), t.getInt("Count"), t.getString("Ench"), t.getInt("Lvl")); }

    /**
     * The model's line: one trader per line, most recently seen first, with deals as {@code pay -> get (left N)}. Distance is from
     * {@code from} when the trader was seen in the same dimension; the minutes since the sighting are read at {@code now}, so the
     * caller refreshes the synced copy now and then (the entity does, every 5 seconds). Empty when nothing is known.
     */
    String text(BlockPos from, String dimension, long now) {
        List<Entry> recent = new ArrayList<>(entries.values());
        recent.sort((a, b) -> Long.compare(b.seen(), a.seen()));
        StringBuilder text = new StringBuilder();
        for (Entry e : recent) {
            StringBuilder line = new StringBuilder("\n- ").append(e.kind()).append(" ур.").append(e.level()).append(e.fresh() ? " (ещё не торговал)" : "")
                    .append(" на ").append(e.pos().toShortString()).append(e.dimension().equals(dimension) ? " (" + Math.round(Math.sqrt(e.pos().distSqr(from))) + " блоков)" : " (в другом измерении)")
                    .append(", видел ").append(now - e.seen() < 1200 ? "только что" : (now - e.seen()) / 1200 + " мин назад").append(':');
            for (Deal d : e.deals()) {
                line.append(' ').append(side(d.give())).append(d.give2() == null ? "" : "+" + side(d.give2())).append("->").append(side(d.get()))
                        .append(d.left() <= 0 ? " (кончилось);" : " (осталось " + d.left() + ");");
            }
            if (text.length() + line.length() > MAX_TEXT) break;
            text.append(line);
        }
        return text.isEmpty() ? "" : "Торговцы, которых он расспросил (сделки настоящие: отдать -> получить, числа — штуки, ID без minecraft:)" + text;
    }

    /** «15 emerald», «1 enchanted_book[mending 1]»: the count apart from the ID, which goes without the minecraft: the model adds back. */
    private static String side(Stack s) {
        return s.count() + " " + bare(s.item()) + (s.enchantment().isEmpty() ? "" : "[" + bare(s.enchantment()) + " " + s.level() + "]");
    }

    private static String bare(String id) { return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id; }

    private static Stack stack(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchantments.isEmpty()) enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        String enchantment = "";
        int level = 0;
        for (var found : enchantments.entrySet()) {
            enchantment = found.getKey().unwrapKey().map(key -> key.location().toString()).orElse("");
            level = found.getIntValue();
            break;
        }
        return new Stack(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), enchantment, level);
    }
}

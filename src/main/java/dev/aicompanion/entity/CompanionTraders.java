package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * What the companion knows about the traders he has talked to: who, where, and the real offers read on the server. Held on the
 * entity only for now (ponytail: no NBT yet, a reload forgets; the save comes with its own ticket).
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
    Entry remember(AbstractVillager trader, ServerLevel level) {
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

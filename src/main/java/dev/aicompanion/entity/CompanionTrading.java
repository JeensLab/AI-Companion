package dev.aicompanion.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * trade_items, one trade: the trader is chosen among the live ones within {@link CompanionScouting#RADIUS} blocks (fixed in his trade
 * before fresh, cheaper, nearer), walked up to within a hand's reach, and the deal is made for real: the price (the deal's own, no
 * player's discounts) leaves the bag, the deal is used once, the villager gets his experience, and the goods are dropped by him and
 * picked up like a harvest. A fresh villager is traded with only on the player's word: the first deal fixes his goods.
 */
final class CompanionTrading {
    /** Three minutes to get to a trader. */
    private static final int LIMIT = 20 * 60 * 3;
    private static final int LOOK_TICKS = 20;
    private static final String OUT = "Кончилось: сделка исчерпана, торговец пополнит запас после работы. Сходить позже?";
    /** A trader whose window a player has open: his offers carry that player's discount then, and the game lets nobody else trade. */
    private static final String BUSY = "Торговец занят: у него открыто окно торговли. Закрой — и я обменяюсь.";

    private record Candidate(UUID id, boolean fresh, int price, double distance, int missing, String missingText) { }

    final CompanionWork w;
    private final CompanionTraderWalk walk;
    private CompanionTradeOrder order;
    private final List<UUID> queue = new ArrayList<>();
    private UUID current;
    private long startedAt, lookUntil;
    private boolean paid;
    private int bagBefore;
    private String report = "", lastReason = "";
    private ItemStack goods = ItemStack.EMPTY;

    CompanionTrading(CompanionWork w) { this.w = w; walk = new CompanionTraderWalk(w); }

    /** Null if the order is taken; otherwise the refusal, in words, with nothing paid. */
    CompanionActionResult begin(String packed) {
        cancel();
        order = CompanionTradeOrder.parse(packed);
        if (order == null) return CompanionActionResult.failed("Не понял, что обменять.");
        if (!(w.owner.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Не могу осмотреться.");
        List<AbstractVillager> near = CompanionScouting.nearby(level, w.owner);
        if (near.isEmpty()) return CompanionActionResult.failed("Торговцев рядом не вижу.");
        boolean matched = false, stocked = false, awake = false, free = false, allowed = false;
        List<Candidate> candidates = new ArrayList<>();
        for (AbstractVillager trader : near) {
            List<MerchantOffer> offers = matching(trader);
            if (offers.isEmpty()) continue;
            matched = true;
            List<MerchantOffer> inStock = offers.stream().filter(offer -> !offer.isOutOfStock()).toList();
            if (inStock.isEmpty()) continue;
            stocked = true;
            if (trader.isSleeping()) continue;
            awake = true;
            if (trader.isTrading()) continue;
            free = true;
            boolean fresh = fresh(trader);
            if (fresh && !order.consent()) continue;
            allowed = true;
            candidates.add(candidate(level, trader, cheapest(inStock), fresh));
        }
        if (!matched) return CompanionActionResult.failed("Такого обмена тут нет.");
        if (!stocked) return CompanionActionResult.failed(OUT);
        if (!awake) return CompanionActionResult.failed("Торговец спит, сходим утром.");
        if (!free) return CompanionActionResult.failed(BUSY);
        if (!allowed) return CompanionActionResult.failed("Такая сделка есть только у жителя, который ещё не торговал: после обмена товар у него закрепится. Меняем?");
        candidates.sort(Comparator.comparing(Candidate::fresh).thenComparingInt(Candidate::price).thenComparingDouble(Candidate::distance));
        List<Candidate> affordable = candidates.stream().filter(candidate -> candidate.missing() == 0).toList();
        if (affordable.isEmpty()) return CompanionActionResult.failed("Не хватает платы: " + candidates.getFirst().missingText() + ".");
        affordable.forEach(candidate -> queue.add(candidate.id()));
        return null;
    }

    void cancel() {
        if (current != null || !queue.isEmpty()) w.route.stop();
        queue.clear(); current = null; order = null; paid = false; report = lastReason = ""; goods = ItemStack.EMPTY;
        startedAt = lookUntil = 0; bagBefore = 0;
        walk.end();
    }

    void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (startedAt == 0) startedAt = now;
        // The trade is made and the goods are being picked up (the work waits while they lie): count what came.
        if (paid) { finishAfterPickup(); return; }
        if (now - startedAt > LIMIT) { w.finish(false, "Не добрался до торговца: слишком долго."); return; }
        if (current == null) {
            if (queue.isEmpty()) { w.finish(false, lastReason.isEmpty() ? "Не дотянулся до торговца." : lastReason); return; }
            current = queue.removeFirst(); walk.begin(now); lookUntil = 0;
        }
        if (!(level.getEntity(current) instanceof AbstractVillager trader) || !trader.isAlive()) { next(); return; }
        if (trader.isSleeping()) { lastReason = "Торговец спит, сходим утром."; next(); return; }
        if (!walk.inReach(trader)) {
            if (walk.approach(level, trader, now) == CompanionTraderWalk.Step.GAVE_UP) next();
            return;
        }
        w.route.stop();
        w.owner.getLookControl().setLookAt(trader, 30, 30);
        trader.getLookControl().setLookAt(w.owner, 30, 30);
        w.owner.workStatus("Меняюсь с торговцем.");
        if (lookUntil == 0) lookUntil = now + LOOK_TICKS;
        if (now < lookUntil) return;
        trade(level, trader);
    }

    private void next() { current = null; walk.end(); }

    /** Looked at the trader long enough: the deal as it is now is checked again and made, or this trader is let go with a reason. */
    private void trade(ServerLevel level, AbstractVillager trader) {
        if (trader.isTrading()) { lastReason = BUSY; next(); return; }
        List<MerchantOffer> inStock = matching(trader).stream().filter(offer -> !offer.isOutOfStock()).toList();
        if (inStock.isEmpty()) { lastReason = OUT; next(); return; }
        MerchantOffer offer = cheapest(inStock);
        Candidate shortage = candidate(level, trader, offer, fresh(trader));
        if (shortage.missing() > 0) { lastReason = "Не хватает платы: " + shortage.missingText() + "."; next(); return; }
        ItemStack costA = offer.getCostA(), costB = offer.getCostB(), result = offer.getResult().copy();
        List<String> prices = new ArrayList<>(List.of(CompanionScouting.text(level, CompanionTraders.stack(costA))));
        w.items.takeCarried(offer.getItemCostA()::test, costA.getCount());
        if (!costB.isEmpty() && offer.getItemCostB().isPresent()) {
            w.items.takeCarried(offer.getItemCostB().get()::test, costB.getCount());
            prices.add(CompanionScouting.text(level, CompanionTraders.stack(costB)));
        }
        // The deal is used and the villager gets his experience and level as the game gives them; notifyTrade is not called (it announces a trade with no player).
        offer.increaseUses();
        trader.rewardTradeXp(offer);
        trader.playSound(trader.getNotifyTradeSound(), 1.0f, 1.0f);
        w.owner.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        goods = result;
        bagBefore = w.items.countCarried(result.getItem());
        throwGoods(level, trader, result);
        w.owner.traders().remember(trader, level);
        w.owner.refreshTraders();
        String kind = kind(trader);
        report = "Обменял " + String.join(" и ", prices) + " на " + CompanionScouting.text(level, CompanionTraders.stack(result)) + " (торговец: "
                + CompanionScouting.profession(kind).toLowerCase(java.util.Locale.ROOT) + ").";
        paid = true;
        w.route.stop();
    }

    /** The trader puts the goods on the ground toward him, as a villager gives; he picks them up like his harvest. */
    private void throwGoods(ServerLevel level, AbstractVillager trader, ItemStack result) {
        Vec3 from = trader.position().add(0, trader.getEyeHeight() - .3, 0);
        Vec3 toward = w.owner.getEyePosition().subtract(from).normalize().scale(.25);
        ItemEntity drop = new ItemEntity(level, from.x, from.y, from.z, result);
        drop.setDeltaMovement(toward.x, toward.y + .1, toward.z);
        drop.setPickUpDelay(10);
        if (level.addFreshEntity(drop)) w.owner.claimDrops(List.of(drop));
    }

    private void finishAfterPickup() {
        int got = w.items.countCarried(goods.getItem()) - bagBefore;
        w.finish(true, got >= goods.getCount() ? report : report + " Товар подобрать не вышло: он лежит рядом.");
    }

    // ---- choosing ----

    private static String kind(AbstractVillager trader) {
        return trader instanceof Villager villager ? BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).getPath() : CompanionTraders.WANDERING;
    }

    /** A fresh villager has never traded: the first deal fixes his profession and goods (a wandering trader never is). */
    private static boolean fresh(AbstractVillager trader) { return trader instanceof Villager villager && villager.getVillagerXp() == 0 && villager.getVillagerData().getLevel() <= 1; }

    private static int price(MerchantOffer offer) { return offer.getCostA().getCount() + offer.getCostB().getCount(); }

    private static MerchantOffer cheapest(List<MerchantOffer> offers) { return offers.stream().min(Comparator.comparingInt(CompanionTrading::price)).orElseThrow(); }

    /** The trader's offers that give what is asked (and take what is named, if one is). */
    private List<MerchantOffer> matching(AbstractVillager trader) {
        if (!order.profession().isEmpty() && !kind(trader).equals(order.profession())) return List.of();
        List<MerchantOffer> found = new ArrayList<>();
        for (MerchantOffer offer : trader.getOffers()) {
            CompanionTraders.Stack result = CompanionTraders.stack(offer.getResult());
            if (!result.item().equals(order.get())) continue;
            if (!order.enchantment().isEmpty() && (!result.enchantment().equals(order.enchantment()) || order.level() != 0 && result.level() != order.level())) continue;
            if (!order.pay().isEmpty() && !pays(offer.getCostA()) && !(offer.getItemCostB().isPresent() && pays(offer.getCostB()))) continue;
            found.add(offer);
        }
        return found;
    }

    private boolean pays(ItemStack cost) { return BuiltInRegistries.ITEM.getKey(cost.getItem()).toString().equals(order.pay()); }

    private Candidate candidate(ServerLevel level, AbstractVillager trader, MerchantOffer offer, boolean fresh) {
        int missingA = missing(offer.getItemCostA(), offer.getCostA().getCount());
        Optional<ItemCost> second = offer.getItemCostB();
        int missingB = second.isPresent() ? missing(second.get(), offer.getCostB().getCount()) : 0;
        StringBuilder text = new StringBuilder();
        if (missingA > 0) text.append(CompanionScouting.text(level, CompanionTraders.stack(offer.getCostA().copyWithCount(missingA))));
        if (missingB > 0) text.append(text.isEmpty() ? "" : " и ").append(CompanionScouting.text(level, CompanionTraders.stack(offer.getCostB().copyWithCount(missingB))));
        return new Candidate(trader.getUUID(), fresh, price(offer), trader.distanceToSqr(w.owner), missingA + missingB, text.toString());
    }

    private int missing(ItemCost cost, int need) {
        int have = 0;
        for (ItemStack stack : w.items.carriedItems()) if (cost.test(stack)) have += stack.getCount();
        return Math.max(0, need - have);
    }
}

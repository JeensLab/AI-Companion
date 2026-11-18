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
 * trade_items: the trader is chosen among the live ones within {@link CompanionScouting#RADIUS} blocks (fixed in his trade
 * before fresh, cheaper, nearer), walked up to within a hand's reach, and the deal is made for real: the price (the deal's own, no
 * player's discounts) leaves the bag, the deal is used once, the villager gets his experience, and the goods are dropped by him and
 * picked up like a harvest. A fresh villager is traded with only on the player's word: the first deal fixes his goods.
 * The deal is repeated as the order counts (pieces of the goods; for a sale the pieces of what he gives; the last trade whole; no number
 * is one trade, or for a sale everything of it he has): with the same trader while his deal lasts, then with the next one with the same
 * deal. It stops when the count is reached, the price is gone, the bag will not take the goods, or no deal is left; the report says which.
 * When no live trader near has the deal, the memory is looked in: a remembered trader of this dimension with it, within
 * {@link CompanionScouting#FAR_RADIUS} blocks, is walked to ({@link CompanionFarWalk}) and traded with by his live offers as near;
 * one who is not there is forgotten and the next remembered one is tried.
 */
final class CompanionTrading {
    /** Three minutes to get to a trader. */
    private static final int LIMIT = 20 * 60 * 3;
    private static final int LOOK_TICKS = 20;
    private static final String OUT = "Кончилось: сделка исчерпана, торговец пополнит запас после работы. Сходить позже?";
    /** A trader whose window a player has open: his offers carry that player's discount then, and the game lets nobody else trade. */
    private static final String FRESH = "Такая сделка есть только у жителя, который ещё не торговал: после обмена товар у него закрепится. Меняем?";
    private static final String BUSY = "Торговец занят: у него открыто окно торговли. Закрой — и я обменяюсь.";

    private record Candidate(UUID id, boolean fresh, int price, double distance, int missing, String missingText) { }

    final CompanionWork w;
    private final CompanionTraderWalk walk;
    private CompanionTradeOrder order;
    private final List<UUID> queue = new ArrayList<>();
    private UUID current;
    /** The remembered traders to walk to, in order, and the one walked to now. */
    private final List<UUID> farQueue = new ArrayList<>();
    private final CompanionFarWalk farWalk;
    private UUID farId;
    /** Set by {@link #choose}: no live trader near had the deal (the refusal is then only the last word if the memory has none). */
    private boolean noDeal;
    private long startedAt, lookUntil;
    private boolean paid, bagFull;
    private int bagBefore, trades, done, units;
    /** Why this trader was let go: the full refusal when nothing was traded, the short reason for the report when something was. */
    private String lastReason = "", lastWhy = "";
    private ItemStack goods = ItemStack.EMPTY, paidA = ItemStack.EMPTY, paidB = ItemStack.EMPTY, gotGoods = ItemStack.EMPTY;

    CompanionTrading(CompanionWork w) { this.w = w; walk = new CompanionTraderWalk(w); farWalk = new CompanionFarWalk(w); }

    /** Null if the order is taken; otherwise the refusal, in words, with nothing paid. */
    CompanionActionResult begin(String packed) {
        cancel();
        order = CompanionTradeOrder.parse(packed);
        if (order == null) return CompanionActionResult.failed("Не понял, что обменять.");
        if (!(w.owner.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Не могу осмотреться.");
        String refusal = choose(level);
        if (refusal == null) return null;
        return noDeal ? remembered(level, refusal) : CompanionActionResult.failed(refusal);
    }

    /** Fills the queue with the live traders near him that will do, in the order the class says; else the refusal, in words, and {@link #noDeal}. */
    private String choose(ServerLevel level) {
        noDeal = false;
        List<AbstractVillager> near = CompanionScouting.nearby(level, w.owner);
        if (near.isEmpty()) { noDeal = true; return "Торговцев рядом не вижу."; }
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
        if (!matched) { noDeal = true; return "Такого обмена тут нет."; }
        if (!stocked) return OUT;
        if (!awake) return "Торговец спит, сходим утром.";
        if (!free) return BUSY;
        if (!allowed) return FRESH;
        candidates.sort(Comparator.comparing(Candidate::fresh).thenComparingInt(Candidate::price).thenComparingDouble(Candidate::distance));
        List<Candidate> affordable = candidates.stream().filter(candidate -> candidate.missing() == 0).toList();
        if (affordable.isEmpty()) return "Не хватает платы: " + candidates.getFirst().missingText() + ".";
        affordable.forEach(candidate -> queue.add(candidate.id()));
        return null;
    }

    /**
     * No live trader near has the deal: the memory's traders that have it. None at all: {@code refusal} stands. None in this dimension
     * within the far radius: he asks to be led. Else they are lined up as near (fixed before fresh, cheaper, nearer) by the remembered
     * deals, and the walk to the first begins on the first tick.
     */
    private CompanionActionResult remembered(ServerLevel level, String refusal) {
        List<CompanionTraders.Entry> have = w.owner.traders().entries().stream().filter(entry -> rememberedDeal(entry) != null).toList();
        if (have.isEmpty()) return CompanionActionResult.failed(refusal);
        String dimension = level.dimension().location().toString();
        double reach = (double) CompanionScouting.FAR_RADIUS * CompanionScouting.FAR_RADIUS;
        List<CompanionTraders.Entry> reachable = have.stream().filter(entry -> entry.dimension().equals(dimension) && entry.pos().distSqr(w.owner.blockPosition()) <= reach).toList();
        if (reachable.isEmpty()) return CompanionActionResult.failed(CompanionScouting.TOO_FAR);
        List<CompanionTraders.Entry> stocked = reachable.stream().filter(entry -> rememberedDeal(entry).left() > 0).toList();
        if (stocked.isEmpty()) return CompanionActionResult.failed(OUT);
        List<CompanionTraders.Entry> allowed = stocked.stream().filter(entry -> !entry.fresh() || order.consent()).toList();
        if (allowed.isEmpty()) return CompanionActionResult.failed(FRESH);
        List<CompanionTraders.Entry> sorted = new ArrayList<>(allowed);
        sorted.sort(Comparator.comparing(CompanionTraders.Entry::fresh).thenComparingInt(entry -> price(rememberedDeal(entry)))
                .thenComparingDouble(entry -> entry.pos().distSqr(w.owner.blockPosition())));
        for (CompanionTraders.Entry entry : sorted) if (missingText(level, rememberedDeal(entry)).isEmpty()) farQueue.add(entry.id());
        if (farQueue.isEmpty()) return CompanionActionResult.failed("Не хватает платы: " + missingText(level, rememberedDeal(sorted.getFirst())) + ".");
        return null;
    }

    /** The remembered trader's cheapest deal that gives what is asked (and takes what is named), one with stock first; or null. */
    private CompanionTraders.Deal rememberedDeal(CompanionTraders.Entry entry) {
        if (!order.profession().isEmpty() && !entry.kind().equals(order.profession())) return null;
        CompanionTraders.Deal best = null;
        for (CompanionTraders.Deal deal : entry.deals()) {
            if (!deal.get().item().equals(order.get())) continue;
            if (!order.enchantment().isEmpty() && (!deal.get().enchantment().equals(order.enchantment()) || order.level() != 0 && deal.get().level() != order.level())) continue;
            if (!order.pay().isEmpty() && !deal.give().item().equals(order.pay()) && (deal.give2() == null || !deal.give2().item().equals(order.pay()))) continue;
            if (best == null || deal.left() > 0 && (best.left() <= 0 || price(deal) < price(best))) best = deal;
        }
        return best;
    }

    private static int price(CompanionTraders.Deal deal) { return deal.give().count() + (deal.give2() == null ? 0 : deal.give2().count()); }

    /** What the bag lacks to pay the remembered deal once, in words; empty when it has it. */
    private String missingText(ServerLevel level, CompanionTraders.Deal deal) {
        List<String> lacking = new ArrayList<>();
        for (CompanionTraders.Stack cost : deal.give2() == null ? List.of(deal.give()) : List.of(deal.give(), deal.give2())) {
            int have = 0;
            for (ItemStack stack : w.items.carriedItems()) if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(cost.item())) have += stack.getCount();
            if (have < cost.count()) lacking.add(CompanionScouting.text(level, new CompanionTraders.Stack(cost.item(), cost.count() - have, cost.enchantment(), cost.level())));
        }
        return String.join(" и ", lacking);
    }

    void cancel() {
        if (current != null || !queue.isEmpty() || farWalk.active()) w.route.stop();
        farWalk.cancel();
        queue.clear(); farQueue.clear(); current = farId = null; order = null; paid = bagFull = false; lastReason = lastWhy = ""; goods = paidA = paidB = gotGoods = ItemStack.EMPTY;
        startedAt = lookUntil = 0; bagBefore = trades = done = units = 0;
        walk.end();
    }

    void tick(ServerLevel level) {
        long now = level.getGameTime();
        // The trade is made and the goods are being picked up (the work waits while they lie): count what came.
        if (paid) { afterPickup(level, now); return; }
        if (farWalk.active()) { far(level, now); return; }
        if (startedAt == 0) startedAt = now;
        if (now - startedAt > LIMIT) {
            if (trades > 0) finishTrades(level, "долго шёл к торговцу");
            else w.finish(false, "Не добрался до торговца: слишком долго.");
            return;
        }
        if (current == null) {
            if (queue.isEmpty() && startFar()) return;
            if (queue.isEmpty()) {
                if (trades > 0) finishTrades(level, lastWhy.isEmpty() ? "других с такой сделкой нет" : lastWhy);
                else w.finish(false, lastReason.isEmpty() ? "Не дотянулся до торговца." : lastReason);
                return;
            }
            current = queue.removeFirst(); walk.begin(now); lookUntil = 0;
        }
        if (!(level.getEntity(current) instanceof AbstractVillager trader) || !trader.isAlive()) { next(); return; }
        if (trader.isSleeping()) { lastReason = "Торговец спит, сходим утром."; lastWhy = "торговец спит"; next(); return; }
        if (!walk.inReach(trader)) {
            if (walk.approach(level, trader, now) == CompanionTraderWalk.Step.GAVE_UP) { lastWhy = "не дотянулся до торговца"; next(); }
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

    /** The walk to the next remembered trader begins; false when none is left (one forgotten since the line was made is passed). */
    private boolean startFar() {
        while (!farQueue.isEmpty()) {
            farId = farQueue.removeFirst();
            var entry = w.owner.traders().entry(farId);
            if (entry != null) { farWalk.begin(entry.pos()); return true; }
        }
        return false;
    }

    /**
     * The way to a remembered trader, then the choice among the live traders there as near. He is not there (not found alive): he is
     * forgotten; he is, but has not the deal now: his offers are read again, so the memory is true; either way the next one is tried.
     */
    private void far(ServerLevel level, long now) {
        switch (farWalk.step(now, "Иду к торговцу, которого помню.")) {
            case WALKING -> { }
            case ARRIVED -> {
                startedAt = now;
                if (level.getEntity(farId) instanceof AbstractVillager trader && trader.isAlive()) {
                    w.owner.traders().remember(trader, level);
                    String refusal = choose(level);
                    if (refusal != null) lastReason = refusal;
                } else {
                    w.owner.traders().forget(farId);
                    lastReason = "Торговца, которого помнил, на месте нет: забыл его.";
                }
                w.owner.refreshTraders();
                farId = null;
            }
            case TIMEOUT -> farLost("Шёл к торговцу слишком долго, не дошёл.");
            case FAILED -> farLost("До торговца, которого помню, дойти не смог: нет пути.");
        }
    }

    private void farLost(String reason) {
        dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "trading_far_failed", "entity", w.owner.getUUID(), "to", farWalk.target().toShortString());
        farWalk.cancel();
        w.route.stop();
        lastReason = reason;
        farId = null;
    }

    /** Looked at the trader long enough: the deal as it is now is checked again and made, or this trader is let go with a reason. */
    private void trade(ServerLevel level, AbstractVillager trader) {
        if (trader.isTrading()) { lastReason = BUSY; lastWhy = "торговец занят"; next(); return; }
        List<MerchantOffer> inStock = matching(trader).stream().filter(offer -> !offer.isOutOfStock()).toList();
        if (inStock.isEmpty()) { lastReason = OUT; lastWhy = "лимит сделки кончился, других с такой сделкой нет"; next(); return; }
        MerchantOffer offer = cheapest(inStock);
        Candidate shortage = candidate(level, trader, offer, fresh(trader));
        if (shortage.missing() > 0) {
            lastReason = "Не хватает платы: " + shortage.missingText() + ".";
            // The bag is the same for every trader: with a trade made, the price is simply gone, and the rest of the line is not walked.
            if (trades > 0) { queue.clear(); lastWhy = "плата кончилась"; }
            next(); return;
        }
        ItemStack costA = offer.getCostA(), costB = offer.getCostB(), result = offer.getResult().copy();
        units = order.sale() ? (pays(costA) ? costA : costB).getCount() : 0;
        w.items.takeCarried(offer.getItemCostA()::test, costA.getCount());
        paidA = add(paidA, costA);
        if (!costB.isEmpty() && offer.getItemCostB().isPresent()) {
            w.items.takeCarried(offer.getItemCostB().get()::test, costB.getCount());
            paidB = add(paidB, costB);
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
        gotGoods = add(gotGoods, result);
        trades++;
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

    /** The goods are in the bag (or could not be): the order is done, or goes on with the same trader (his deal may still hold). */
    private void afterPickup(ServerLevel level, long now) {
        int got = w.items.countCarried(goods.getItem()) - bagBefore;
        done += order.sale() ? units : got;
        if (got < goods.getCount()) { bagFull = true; finishTrades(level, "сумка полна, товар лежит рядом"); return; }
        if (order.count() > 0 ? done >= order.count() : !order.sale()) { finishTrades(level, ""); return; }
        paid = false; lookUntil = now;
    }

    /** The honest end of a trade order: what was traded for what, and, when the number was not reached or none was named, why it stopped. */
    private void finishTrades(ServerLevel level, String why) {
        String paidText = List.of(paidA, paidB).stream().filter(stack -> !stack.isEmpty()).map(stack -> CompanionScouting.text(level, CompanionTraders.stack(stack))).collect(java.util.stream.Collectors.joining(" и "));
        String goodsText = CompanionScouting.text(level, CompanionTraders.stack(gotGoods));
        String base = order.sale() ? "Продал " + paidText + " за " + goodsText : "Купил " + goodsText + " за " + paidText;
        boolean reached = order.count() == 0 || done >= order.count();
        String message = base + (order.count() > 0 && done < order.count() ? ", это " + done + " из " + order.count() : "") + (why.isEmpty() ? "." : ": " + why + ".");
        w.finish(reached && !bagFull, message);
    }

    private static ItemStack add(ItemStack sum, ItemStack more) { return sum.isEmpty() ? more.copy() : sum.copyWithCount(sum.getCount() + more.getCount()); }

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

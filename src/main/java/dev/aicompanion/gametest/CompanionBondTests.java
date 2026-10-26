package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Storage and pure rules of per-owner memory, bond and happenings: limits, secret filter, daily caps, gift ledger, NBT. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBondTests {
    private static final long DAY = CompanionBond.DAY_TICKS;

    @GameTest(template="empty")
    public static void memoryLimitsEvictPromisesLast(GameTestHelper h) {
        var m = new CompanionMemory();
        for (int i = 0; i < 3; i++) m.remember(CompanionMemory.PROMISE, "Обещал принести подарок " + i);
        m.remember(CompanionMemory.PLAYER_FACT, "Любит блюдо 0");
        m.remember(CompanionMemory.PLAYER_FACT, "любит блюдо 0.");
        for (int i = 1; i < 60; i++) m.remember(CompanionMemory.PLAYER_FACT, "Любит блюдо " + i);
        h.assertTrue(m.entries().size() == CompanionMemory.MAX_MEMORIES, "Memory not capped at 40: " + m.entries().size());
        h.assertTrue(m.entries().stream().filter(e -> e.kind().equals(CompanionMemory.PROMISE)).count() == 3, "A promise was evicted while facts remained");
        h.assertTrue(has(m, "Любит блюдо 0") && weightOf(m, "Любит блюдо 0") == 2, "Reinforced fact did not outlive lighter ones");
        h.assertTrue(has(m, "Любит блюдо 59") && !has(m, "Любит блюдо 1"), "Oldest light fact was not the one evicted");

        var promises = new CompanionMemory();
        for (int i = 0; i < CompanionMemory.MAX_MEMORIES; i++) promises.remember(CompanionMemory.PROMISE, "Обещание " + i);
        h.assertTrue(promises.remember(CompanionMemory.JOKE, "Шутка про лаву") == CompanionMemory.Outcome.ADDED, "Joke refused by a full memory");
        h.assertTrue(promises.entries().size() == 40 && has(promises, "Шутка про лаву") && !has(promises, "Обещание 0") && has(promises, "Обещание 1"), "Only promises left: the oldest promise should make room");
        promises.remember(CompanionMemory.PROMISE, "Обещание 40");
        h.assertTrue(!has(promises, "Шутка про лаву") && has(promises, "Обещание 1") && has(promises, "Обещание 40"), "A joke should go before any promise");

        var lengths = new CompanionMemory();
        h.assertTrue(lengths.remember(CompanionMemory.JOKE, "я".repeat(160)) == CompanionMemory.Outcome.ADDED, "160 characters refused");
        h.assertTrue(lengths.remember(CompanionMemory.JOKE, "я".repeat(161)) == CompanionMemory.Outcome.REJECTED, "161 characters accepted");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void duplicateMemoryRaisesWeight(GameTestHelper h) {
        var m = new CompanionMemory();
        h.assertTrue(m.remember(CompanionMemory.PLAYER_FACT, "Любит играть ночью") == CompanionMemory.Outcome.ADDED, "First memory not added");
        h.assertTrue(m.remember(CompanionMemory.PLAYER_FACT, "  любит   играть НОЧЬЮ. ") == CompanionMemory.Outcome.REINFORCED, "Duplicate not recognised");
        h.assertTrue(m.entries().size() == 1 && m.entries().getFirst().weight() == 2, "Duplicate added instead of raising weight: " + m.entries());
        m.remember(CompanionMemory.JOKE, "Любит играть ночью");
        h.assertTrue(m.entries().size() == 2, "Same text of another kind should be separate");
        for (int i = 0; i < 30; i++) m.remember(CompanionMemory.PLAYER_FACT, "Любит играть ночью");
        h.assertTrue(weightOf(m, "Любит играть ночью") <= CompanionMemory.MAX_WEIGHT && m.entries().size() == 2, "Weight not capped");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void secretFilterRejectsKeysCoordinatesAndIds(GameTestHelper h) {
        List<String> bad = List.of(
                "Мой ключ sk-abcdef1234567890abcdef", "Пароль: hunter2", "my password = hunter2", "token=abc123", "Токен — abcdef",
                "Дом на x: 120 y: 64 z: -300", "Дом на 120 64 -300", "Ходит с minecraft:diamond_sword", "Любит aicompanion:companion",
                "Ключ QWxhZGRpbjpvcGVuIHNlc2FtZQ==QWxhZGRpbjpvcGVu", "Хеш 0123456789abcdef0123456789abcdef", "Bearer abcdefgh12345678", "Координаты базы секретные",
                "мой пароль hunter2", "password is hunter2", "пароль – qwerty", "пароль это correct horse battery", "my api key abc123xyz",
                "ASIAabcdefgh12345678", "ghs_abcdefgh12345678", "github_pat_11ABCDEFG0abcdefgh", "pass​word: hunter2",
                "sk-­abcdef1234567890abcdef", "ｐａｓｓｗｏｒｄ = hunter2");
        for (String text : bad) {
            var m = new CompanionMemory();
            h.assertTrue(m.remember(CompanionMemory.PLAYER_FACT, text) == CompanionMemory.Outcome.REJECTED && m.entries().isEmpty(), "Stored: " + text);
            h.assertTrue(CompanionMemory.isSensitive(text), "Not flagged: " + text);
        }
        var m = new CompanionMemory();
        h.assertTrue(m.remember("secret_kind", "Любит арбузы") == CompanionMemory.Outcome.REJECTED, "Unknown kind accepted");
        h.assertTrue(m.remember(CompanionMemory.PLAYER_FACT, "   ") == CompanionMemory.Outcome.REJECTED, "Blank accepted");
        h.assertTrue(!m.setCallMe("sk-abcdef1234567890abcdef") && !m.addLike("minecraft:stone") && m.callMe().isEmpty() && m.likes().isEmpty(), "Secret got in as a name or preference");
        for (String text : List.of("Любит строить дома у воды", "Просил разбудить в 10:30", "Зовёт меня Шефом, а дом назвал Берлогой", "Ходим втроём: он, я и пёс", "Ключевой момент был в постройке моста"))
            h.assertTrue(m.remember(CompanionMemory.PLAYER_FACT, text) == CompanionMemory.Outcome.ADDED, "Harmless text refused: " + text);
        h.succeed();
    }

    @GameTest(template="empty")
    public static void recentTalkMasksSecretsBeforeSave(GameTestHelper h) {
        var m = new CompanionMemory();
        m.recordTalk(true, "Запомни мой пароль: hunter2 и всё");
        m.recordTalk(true, "вот ключ sk-abcdef1234567890abcdef ок");
        m.recordTalk(false, "База на 120 64 -300, не забудь");
        m.recordTalk(true, "Привет, Олег");
        String saved = m.save().toString();
        for (String leaked : List.of("hunter2", "sk-abcdef", "120 64 -300")) h.assertTrue(!saved.contains(leaked), "Saved talk leaks " + leaked + ": " + saved);
        h.assertTrue(m.recentTalk().get(0).text().contains(CompanionMemory.MASK) && m.recentTalk().get(0).text().startsWith("Запомни мой"), "Secret not replaced in place: " + m.recentTalk().get(0));
        h.assertTrue(m.recentTalk().get(3).text().equals("Привет, Олег") && m.recentTalk().get(3).fromPlayer() && !m.recentTalk().get(2).fromPlayer(), "Ordinary line or speaker changed");

        for (int i = 0; i < 10; i++) m.recordTalk(i % 2 == 0, "Реплика номер " + i);
        h.assertTrue(m.recentTalk().size() == CompanionMemory.TALK_KEEP && m.recentTalk().getLast().text().equals("Реплика номер 9"), "Talk not trimmed to the last six");
        m.recordTalk(true, "слово ".repeat(60));
        h.assertTrue(m.recentTalk().getLast().text().length() <= CompanionMemory.TALK_TEXT, "Long line not cut to 180");

        var phrases = new CompanionMemory();
        phrases.recordTalk(true, "мой пароль hunter2 вот так");
        phrases.recordTalk(true, "пароль: correct horse battery staple. Дальше привет");
        phrases.recordTalk(true, "pass​word is zebra9 ok");
        phrases.recordTalk(true, "токен ghs_abcdefgh12345678 ок");
        phrases.recordTalk(true, "а".repeat(5000) + " пароль: late");
        String kept = phrases.save().toString();
        for (String leaked : List.of("hunter2", "horse", "battery", "staple", "zebra9", "ghs_abcdefgh"))
            h.assertTrue(!kept.contains(leaked), "Saved talk leaks " + leaked + ": " + kept);
        h.assertTrue(phrases.recentTalk().get(1).text().contains("Дальше привет") && phrases.recentTalk().getLast().text().length() <= CompanionMemory.TALK_TEXT, "Clause end or length cut wrong: " + phrases.recentTalk());

        var bonds = new CompanionBonds();
        var owner = UUID.randomUUID();
        bonds.recordTalk(owner, true, "token: abcdef secret");
        String world = bonds.save(new CompoundTag(), registries(h)).toString();
        h.assertTrue(!world.contains("abcdef") && world.contains(CompanionMemory.MASK), "World data leaks a masked token: " + world);
        h.succeed();
    }

    @GameTest(template="empty")
    public static void bondDecaysTowardBaseAndRespectsDailyCaps(GameTestHelper h) {
        var b = new CompanionBond();
        h.assertTrue(b.score() == CompanionBond.BASE && b.tier() == CompanionBond.Tier.ACQUAINTANCE, "Start is not the base");
        int deeds = 0;
        for (int i = 0; i < 8; i++) deeds += b.sharedDeed(3);
        h.assertTrue(deeds == 5 && b.score() == 25, "Deed cap of +5 a day broken: " + deeds);
        for (int i = 0; i < 5; i++) b.rescued(3);
        h.assertTrue(b.score() == 45 && b.tier() == CompanionBond.Tier.PAL, "Rescues not credited: " + b.score());
        h.assertTrue(b.leftIdleFar(3, CompanionBond.ABANDON_AFTER_TICKS) == 0, "Exactly 20 minutes is not 'longer than'");
        h.assertTrue(b.leftIdleFar(3, CompanionBond.ABANDON_AFTER_TICKS + 1) == -3 && b.leftIdleFar(3, 99999) == 0 && b.score() == 42, "Abandonment is -3 once a day");
        h.assertTrue(b.sharedDeed(4) == 1 && b.score() == 42, "A new day should decay by one then allow deeds again: " + b.score());
        b.advanceTo(14);
        h.assertTrue(b.score() == 32, "Decay is not one point per day: " + b.score());
        b.advanceTo(1000);
        h.assertTrue(b.score() == CompanionBond.BASE, "Did not settle at base");
        b.advanceTo(2000);
        h.assertTrue(b.score() == CompanionBond.BASE, "Decay overshot the base");

        b.hitByPlayer(2000, 2000 * DAY, false); b.hitByPlayer(2000, 2000 * DAY + 700, false);
        h.assertTrue(b.score() == 8 && b.tier() == CompanionBond.Tier.WARY, "Two hits should leave 8: " + b.score());
        b.advanceTo(2001);
        h.assertTrue(b.score() == 9, "Decay from below the base did not rise");
        b.advanceTo(2500);
        h.assertTrue(b.score() == CompanionBond.BASE, "Did not rise back to base");

        var top = new CompanionBond();
        for (int i = 0; i < 30; i++) top.rescued(1);
        h.assertTrue(top.score() == CompanionBond.MAX && top.tier() == CompanionBond.Tier.BEST_FRIEND && top.reasons().equals(List.of(CompanionBond.Reason.RESCUE)), "Score not clamped or reasons repeat");

        var server = h.getLevel().getServer();
        var friend = UUID.randomUUID();
        for (int i = 0; i < 15; i++) CompanionBonds.get(server).rescued(friend, 0);
        var digest = CompanionMemoryDigest.of(server, friend, true, 20 * DAY);
        h.assertTrue(digest.score() == 60 && digest.tier().equals(CompanionBond.Tier.PAL.label()), "The digest shows a bond that has not decayed: " + digest.score() + " " + digest.tier());
        h.succeed();
    }

    @GameTest(template="empty")
    public static void giftLedgerCreditsOnlyNewPeakAcrossDays(GameTestHelper h) {
        h.assertTrue(CompanionBond.classify(new ItemStack(Items.DIAMOND)) == CompanionBond.GiftClass.PRECIOUS
                && CompanionBond.classify(new ItemStack(Items.NETHERITE_INGOT)) == CompanionBond.GiftClass.PRECIOUS
                && CompanionBond.classify(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)) == CompanionBond.GiftClass.PRECIOUS
                && CompanionBond.classify(new ItemStack(Items.IRON_PICKAXE)) == CompanionBond.GiftClass.METAL
                && CompanionBond.classify(new ItemStack(Items.GOLD_INGOT)) == CompanionBond.GiftClass.METAL
                && CompanionBond.classify(new ItemStack(Items.BREAD)) == CompanionBond.GiftClass.FOOD
                && CompanionBond.classify(new ItemStack(Items.COBBLESTONE)) == CompanionBond.GiftClass.MATERIAL
                && CompanionBond.classify(new ItemStack(Items.IRON_NUGGET)) == CompanionBond.GiftClass.MATERIAL
                && CompanionBond.classify(new ItemStack(Items.GOLD_NUGGET)) == CompanionBond.GiftClass.MATERIAL, "Gift classes wrong");
        var b = new CompanionBond();
        String diamond = CompanionBond.kindOf(new ItemStack(Items.DIAMOND));
        h.assertTrue(b.gift(1, List.of(give(Items.DIAMOND, 10))) == 8 && b.score() == 28, "First diamonds should pay 8");
        h.assertTrue(b.gift(1, List.of(give(Items.DIAMOND, -10))) == 0 && b.balance(diamond) == 0 && b.peak(diamond) == 10, "Taking back should keep the peak only");
        h.assertTrue(b.gift(2, List.of(give(Items.DIAMOND, 10))) == 0, "Put-take-put paid twice across a day");
        h.assertTrue(b.gift(2, List.of(give(Items.DIAMOND, 3))) == 8 && b.peak(diamond) == 13, "Growth above the old peak not paid");
        int before = b.score();
        int kinds = b.ledgerKinds();
        h.assertTrue(b.gift(2, List.of(give(Items.IRON_INGOT, -5))) == 0 && b.balance(CompanionBond.kindOf(new ItemStack(Items.IRON_INGOT))) == 0 && b.score() == before, "Taking items never put changed the balance");
        h.assertTrue(b.ledgerKinds() == kinds, "Taking out a kind never put created a ledger entry");

        var daily = new CompanionBond();
        int first = daily.gift(5, List.of(give(Items.DIAMOND_PICKAXE, 1)));
        int second = daily.gift(5, List.of(give(Items.NETHERITE_INGOT, 1)));
        int third = daily.gift(5, List.of(give(Items.DIAMOND_SWORD, 1)));
        h.assertTrue(first == 8 && second == 7 && third == 0, "Daily cap of +15 broken: " + first + "," + second + "," + third);
        h.assertTrue(daily.gift(6, List.of(give(Items.BREAD, 10), give(Items.IRON_INGOT, 2), give(Items.COBBLESTONE, 20))) == 5, "One closed inventory should pay its highest class only");
        h.assertTrue(daily.gift(7, List.of(give(Items.BREAD, 7), give(Items.COBBLESTONE, 15), give(Items.GOLD_NUGGET, 1))) == 0, "Below the pieces threshold still paid");

        var many = new CompanionBond();
        for (int i = 0; i < 70; i++) many.gift(10 + i, List.of(new CompanionBond.ItemChange("test:kind_" + i, 1, CompanionBond.GiftClass.METAL)));
        h.assertTrue(many.ledgerKinds() == CompanionBond.MAX_KINDS && many.peak("test:kind_0") == 0 && many.peak("test:kind_69") == 1, "Ledger not capped with oldest evicted");
        many.advanceTo(2000);
        h.assertTrue(many.gift(2000, List.of(new CompanionBond.ItemChange("test:kind_0", 1, CompanionBond.GiftClass.METAL))) == 5, "An evicted kind should count as new");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void hitPenaltyIsRateLimitedAndCapped(GameTestHelper h) {
        var b = new CompanionBond();
        h.assertTrue(b.hitByPlayer(0, 1000, false) == -6 && b.score() == 14 && b.tier() == CompanionBond.Tier.WARY, "First hit should cost 6");
        h.assertTrue(b.hitByPlayer(0, 1599, false) == 0 && b.score() == 14, "Second hit within 30 s counted");
        h.assertTrue(b.hitByPlayer(0, 1600, false) == -6 && b.score() == 8, "Hit after 30 s not counted");
        h.assertTrue(b.hitByPlayer(0, 9000, false) == 0 && b.score() == 8, "Daily cap of -12 broken");

        h.assertTrue(b.hitByPlayer(1, DAY + 1000, true) == 0 && b.score() == 9 && b.reasons().getLast() == CompanionBond.Reason.HIT, "A hit in a monster fight must not count");
        h.assertTrue(b.hitByPlayer(1, DAY + 1001, false) == -6 && b.score() == 3, "Hit on a new day not counted");

        var fight = new CompanionBond();
        h.assertTrue(fight.hitByPlayer(4, 4 * DAY, false) == -6 && fight.hitByPlayer(4, 4 * DAY + 300, true) == 0, "Setup of the fight case failed");
        h.assertTrue(fight.hitByPlayer(4, 4 * DAY + 650, false) == -6, "A refused hit in a fight must not restart the 30 s pause");
        h.assertTrue(CompanionBond.dayOf(DAY * 3 + 5) == 3, "Day boundary wrong");
        var stamp = new CompanionBond().save();
        stamp.putLong("LastHit", Long.MIN_VALUE);
        h.assertTrue(CompanionBond.load(stamp).hitByPlayer(1, DAY + 1000, false) == -6, "A damaged LastHit stamp stopped hits from counting");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void bondsSaveAndLoadRoundTrip(GameTestHelper h) {
        var owner = UUID.randomUUID();
        var other = UUID.randomUUID();
        var a = new CompanionBonds();
        a.remember(owner, CompanionMemory.PROMISE, "Обещал показать ферму");
        a.remember(owner, CompanionMemory.PLAYER_FACT, "Любит арбузы");
        a.remember(owner, CompanionMemory.PLAYER_FACT, "Любит арбузы");
        a.recordTalk(owner, true, "Привет, Олег");
        a.recordTalk(owner, false, "Здорово");
        a.setCallMe(owner, "Шеф"); a.addLike(owner, "арбузы"); a.addDislike(owner, "крипер");
        a.gift(owner, 5, List.of(give(Items.DIAMOND, 3)));
        a.hitByPlayer(owner, 5, 5 * DAY + 10, false);
        a.rescued(owner, 5);
        for (int i = 0; i < 14; i++) a.happened(owner, "k" + i, "Событие " + i, i * 100L, i == 13 ? CompanionHappenings.Importance.HIGH : CompanionHappenings.Importance.NORMAL);
        a.markSeen(owner, 1_700_000_000_000L);
        a.remember(other, CompanionMemory.JOKE, "Шутка про лаву");

        var b = CompanionBonds.load(a.save(new CompoundTag(), registries(h)), registries(h));
        h.assertTrue(b.memory(owner).entries().equals(a.memory(owner).entries()) && b.memory(owner).entries().size() == 2 && b.memory(owner).entries().get(1).weight() == 2, "Memories differ after reload: " + b.memory(owner).entries());
        h.assertTrue(b.memory(owner).recentTalk().equals(a.memory(owner).recentTalk()) && b.memory(owner).recentTalk().size() == 2, "Talk differs after reload");
        h.assertTrue(b.memory(owner).callMe().equals("Шеф") && b.memory(owner).likes().equals(List.of("арбузы")) && b.memory(owner).dislikes().equals(List.of("крипер")), "Name or preferences lost");
        var bond = b.bond(owner);
        h.assertTrue(bond.score() == 26 && bond.score() == a.bond(owner).score() && bond.reasons().equals(a.bond(owner).reasons()) && bond.reasons().size() == 3, "Bond differs: " + bond.score() + " " + bond.reasons());
        h.assertTrue(bond.peak(CompanionBond.kindOf(new ItemStack(Items.DIAMOND))) == 3, "Gift ledger lost");
        h.assertTrue(b.hitByPlayer(owner, 5, 5 * DAY + 100, false) == 0, "Hit pause lost on reload");
        h.assertTrue(b.hitByPlayer(owner, 5, 5 * DAY + 700, false) == -6, "Hit after the pause refused after reload");
        var events = b.happenings(owner).all();
        h.assertTrue(events.equals(a.happenings(owner).all()) && events.size() == CompanionHappenings.KEEP && events.getFirst().key().equals("k2")
                && events.getLast().importance() == CompanionHappenings.Importance.HIGH && events.getLast().gameTime() == 1300, "Happenings differ or ring wrong: " + events);
        h.assertTrue(b.lastSeen(owner).orElse(-1) == 1_700_000_000_000L && b.lastSeen(other).isEmpty(), "Last seen lost");
        h.assertTrue(b.memory(other).entries().size() == 1 && b.memory(other).entries().getFirst().text().equals("Шутка про лаву") && b.memory(other).callMe().isEmpty(), "Owners mixed up");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void missingBondsDataLoadsEmpty(GameTestHelper h) {
        var owner = UUID.randomUUID();
        var empty = CompanionBonds.load(new CompoundTag(), registries(h));
        h.assertTrue(empty.memory(owner).isEmpty() && empty.memory(owner).entries().isEmpty(), "Memory not empty");
        var bond = empty.bond(owner);
        h.assertTrue(bond.score() == CompanionBond.BASE && bond.tier() == CompanionBond.Tier.ACQUAINTANCE && bond.reasons().isEmpty() && bond.ledgerKinds() == 0, "Bond not at base");
        h.assertTrue(empty.happenings(owner).isEmpty() && empty.lastSeen(owner).isEmpty() && !empty.isDirty(), "Reading created data or marked it dirty");
        h.assertTrue(CompanionBonds.get(h.getLevel().getServer()).memory(owner).isEmpty(), "World storage not empty for an unknown owner");

        var damagedOwner = UUID.randomUUID();
        var memory = new CompoundTag();
        var list = new ListTag();
        list.add(entry(CompanionMemory.PLAYER_FACT, "ключ sk-abcdef1234567890abcdef"));
        list.add(entry("nonsense", "Любит арбузы"));
        list.add(entry(CompanionMemory.PROMISE, "Обещал помочь"));
        memory.put("Memories", list);
        var bondTag = new CompoundTag();
        bondTag.putInt("Score", 9999);
        var slot = new CompoundTag();
        slot.putUUID("Owner", damagedOwner); slot.put("Memory", memory); slot.put("Bond", bondTag);
        var owners = new ListTag();
        owners.add(new CompoundTag());
        owners.add(slot);
        var tag = new CompoundTag();
        tag.put("Owners", owners);
        var damaged = CompanionBonds.load(tag, registries(h));
        h.assertTrue(damaged.memory(damagedOwner).entries().size() == 1 && damaged.memory(damagedOwner).entries().getFirst().text().equals("Обещал помочь"), "Damaged memory not filtered on load");
        h.assertTrue(damaged.bond(damagedOwner).score() == CompanionBond.MAX, "Damaged score not clamped");

        var weighted = new ListTag();
        for (int weight : new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, -5}) {
            var item = entry(CompanionMemory.JOKE, weight < 0 ? "Шутка про лаву" : "Шутка про воду");
            item.putInt("Weight", weight);
            weighted.add(item);
        }
        var weights = new CompoundTag();
        weights.put("Memories", weighted);
        var clamped = CompanionMemory.load(weights).entries();
        h.assertTrue(clamped.size() == 2 && clamped.stream().allMatch(e -> e.weight() >= 1 && e.weight() <= CompanionMemory.MAX_WEIGHT)
                && clamped.getFirst().weight() == CompanionMemory.MAX_WEIGHT && clamped.getLast().weight() == 1, "Damaged weights not clamped: " + clamped);

        var longKey = new CompoundTag();
        longKey.putString("Key", "k".repeat(300)); longKey.putString("Text", "Что-то было"); longKey.putLong("Time", 1);
        var events = new ListTag();
        events.add(longKey);
        var eventsTag = new CompoundTag();
        eventsTag.put("Events", events);
        var loadedEvents = CompanionHappenings.load(eventsTag).all();
        h.assertTrue(loadedEvents.size() == 1 && loadedEvents.getFirst().key().length() == CompanionHappenings.KEY, "Damaged key not capped: " + loadedEvents);
        var crowd = new ListTag();
        for (int i = 0; i < CompanionBonds.MAX_OWNERS + 6; i++) {
            var crowded = new CompoundTag();
            crowded.putUUID("Owner", UUID.randomUUID()); crowded.putLong("LastSeen", 1000 + i);
            crowd.add(crowded);
        }
        var crowdTag = new CompoundTag();
        crowdTag.put("Owners", crowd);
        h.assertTrue(CompanionBonds.load(crowdTag, registries(h)).ownerCount() == CompanionBonds.MAX_OWNERS, "Owners not capped on load");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void memoryByOwnerSurvivesCompanionDeath(GameTestHelper h) {
        var bonds = CompanionBonds.get(h.getLevel().getServer());
        var owner = UUID.randomUUID();
        var first = h.spawn(AICompanionMod.COMPANION.get(), 0, 1, 0);
        first.setNoAi(true); first.setOwnerId(owner);
        bonds.remember(first.getOwnerId(), CompanionMemory.PROMISE, "Обещал не бросать");
        bonds.recordTalk(first.getOwnerId(), true, "Пойдём в шахту");
        bonds.rescued(first.getOwnerId(), 1);
        var firstId = first.getUUID();
        first.discard();

        var second = h.spawn(AICompanionMod.COMPANION.get(), 0, 1, 0);
        second.setNoAi(true); second.setOwnerId(owner);
        h.assertTrue(!second.getUUID().equals(firstId), "The new companion kept the old UUID");
        var seen = bonds.memory(second.getOwnerId());
        h.assertTrue(seen.entries().size() == 1 && seen.entries().getFirst().text().equals("Обещал не бросать") && seen.recentTalk().size() == 1, "New companion does not see the owner's memory");
        h.assertTrue(bonds.bond(second.getOwnerId()).score() == CompanionBond.BASE + CompanionBond.RESCUE_POINTS, "New companion does not see the owner's bond");
        bonds.forget(owner);
        h.assertTrue(bonds.memory(second.getOwnerId()).isEmpty() && bonds.bond(owner).score() == 24, "Forget should clear memory and keep the bond");
        h.succeed();
    }

    private static CompanionBond.ItemChange give(Item item, int delta) { return CompanionBond.change(new ItemStack(item), delta); }

    private static CompoundTag entry(String kind, String text) {
        var tag = new CompoundTag();
        tag.putString("Kind", kind); tag.putString("Text", text); tag.putInt("Weight", 1);
        return tag;
    }

    private static HolderLookup.Provider registries(GameTestHelper h) { return h.getLevel().registryAccess(); }

    private static boolean has(CompanionMemory m, String text) { return m.entries().stream().anyMatch(e -> e.text().equals(text)); }

    private static int weightOf(CompanionMemory m, String text) { return m.entries().stream().filter(e -> e.text().equals(text)).mapToInt(CompanionMemory.Entry::weight).findFirst().orElse(0); }
}

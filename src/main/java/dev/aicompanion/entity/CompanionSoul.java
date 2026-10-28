package dev.aicompanion.entity;

import dev.aicompanion.CompanionPersonality.Trait;
import dev.aicompanion.CompanionTopics;
import dev.aicompanion.CompanionTopics.Topic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * His own part of what the companion remembers about one player: his character shifted towards the player, topics of his own
 * made from their talks, the topics already raised and how the player took each subject. The facts about the player are the
 * memory's. The memory holds this, so it is kept with the world, filled by the same condensing request and forgotten with it.
 * Model output is checked in CompanionMemoryCodec and again here, on load as well.
 */
public final class CompanionSoul {
    public static final int OWN_TOPICS = 15, RAISED = 300, RECENT = 8, MAX_TASTE = 4;
    private final Map<Trait, Integer> character = new EnumMap<>(Trait.class);
    private final List<String> own = new ArrayList<>();
    /** Keys of the topics he has raised, the one raised longest ago first. */
    private final LinkedHashSet<String> raised = new LinkedHashSet<>();
    private final Map<String, Integer> taste = new HashMap<>();
    // The subject he raised last and the player's lines since. Not saved: a topic raised before a reload leaves the taste as it was.
    private String opened = "";
    private int answers;

    /**
     * A topic he has not raised yet: often one of his own, otherwise one from the bank by the player's taste. Without
     * {@code questions} (the player left his last question unanswered) only one that asks nothing.
     */
    public Topic next(RandomGenerator random, boolean questions) {
        List<Topic> fitting = questions ? CompanionTopics.ALL : CompanionTopics.ALL.stream().filter(topic -> !topic.text().contains("?")).toList();
        List<String> mine = questions ? own : own.stream().filter(text -> !text.contains("?")).toList();
        List<Topic> bank = fitting.stream().filter(topic -> !raised.contains(topic.key())).toList();
        if (!mine.isEmpty() && (bank.isEmpty() || random.nextInt(10) < 6)) return CompanionTopics.own(mine.get(random.nextInt(mine.size())));
        // Everything raised: the bank topic raised longest ago comes back first.
        if (bank.isEmpty()) return raised.stream().map(CompanionTopics::bank).filter(Objects::nonNull).filter(fitting::contains).findFirst().orElse(fitting.getFirst());
        double total = 0;
        for (Topic topic : bank) total += weight(topic.category());
        double pick = random.nextDouble() * total;
        for (Topic topic : bank) if ((pick -= weight(topic.category())) < 0) return topic;
        return bank.getLast();
    }

    private double weight(String category) { return Math.max(.25, Math.min(3, 1 + .5 * taste(category))); }

    /** The topic is said: it does not come back soon, and the player's lines until the next topic count as answers to it. */
    public void raised(Topic topic) {
        closeTopic();
        own.removeIf(text -> CompanionTopics.own(text).key().equals(topic.key()));
        raised.remove(topic.key());
        raised.add(topic.key());
        while (raised.size() > RAISED) raised.remove(raised.iterator().next());
        opened = topic.category();
        answers = 0;
    }

    void heard() { if (!opened.isEmpty()) answers++; }

    /** Two answers or more: the player liked the subject; none at all: not his thing. */
    private void closeTopic() {
        if (opened.isEmpty()) return;
        taste.put(opened, Math.max(-MAX_TASTE, Math.min(MAX_TASTE, taste(opened) + (answers >= 2 ? 1 : answers == 0 ? -1 : 0))));
        opened = "";
        answers = 0;
    }

    /**
     * What a condensed talk says: the level of a trait that suits the player and topics for the next talks. One talk cannot turn
     * him into someone else: a step at a time, but what may hurt or annoy the player is toned down at once (Trait.dropsAtOnce).
     */
    public void adapt(Map<Trait, Integer> wanted, List<String> topics) {
        wanted.forEach((trait, target) -> {
            int level = level(trait), goal = Math.max(-2, Math.min(2, target));
            int next = goal < level && trait.dropsAtOnce() ? goal : level + Integer.signum(goal - level);
            if (next == 0) character.remove(trait); else character.put(trait, next);
        });
        for (String text : topics) if (speakable(text) && !known(text)) own.add(text);
        while (own.size() > OWN_TOPICS) own.removeFirst();
    }

    private static boolean speakable(String text) { return CompanionTopics.speakable(text) && !CompanionMemory.isSensitive(text); }

    /** He has this topic, has raised it or has it in the bank, perhaps in other words. */
    private boolean known(String text) {
        List<String> keys = new ArrayList<>(raised);
        CompanionTopics.ALL.forEach(topic -> keys.add(topic.key()));
        own.forEach(old -> keys.add(CompanionTopics.own(old).key()));
        return keys.stream().anyMatch(key -> CompanionTopics.repeats(key, text));
    }

    private int level(Trait trait) { return character.getOrDefault(trait, 0); }

    /** Every trait with its level; 0 for a trait no talk has moved. */
    public Map<Trait, Integer> character() {
        Map<Trait, Integer> all = new EnumMap<>(Trait.class);
        for (Trait trait : Trait.values()) all.put(trait, level(trait));
        return all;
    }

    public int taste(String category) { return taste.getOrDefault(category, 0); }

    public List<String> ownTopics() { return List.copyOf(own); }

    /**
     * For the condensing request: his character now and the topics he has or raised lately, to keep the one and not repeat the other.
     * The raised ones are checked for secrets here, as they leave, not on every load: a copy of the memory is made every two seconds.
     */
    public String brief() {
        List<String> known = new ArrayList<>(own);
        List<String> keys = new ArrayList<>(raised);
        keys.subList(Math.max(0, keys.size() - RECENT), keys.size()).stream().map(CompanionTopics::text)
                .filter(text -> !text.isEmpty() && !CompanionMemory.isSensitive(text)).forEach(known::add);
        return "его характер сейчас=" + String.join(", ", Arrays.stream(Trait.values()).map(trait -> trait.key + "=" + level(trait)).toList())
                + "; его темы в запасе и недавние=" + (known.isEmpty() ? "нет" : String.join(" | ", known));
    }

    public boolean isEmpty() { return character.isEmpty() && own.isEmpty() && raised.isEmpty() && taste.isEmpty(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag traits = new CompoundTag();
        character.forEach((trait, level) -> traits.putInt(trait.key, level));
        tag.put("Character", traits);
        tag.put("Own", strings(own));
        tag.put("Raised", strings(raised));
        CompoundTag likes = new CompoundTag();
        taste.forEach(likes::putInt);
        tag.put("Taste", likes);
        return tag;
    }

    /** Loading re-applies the checks (the raised topics are checked by brief): a hand-edited or damaged save must not smuggle in what live input could not. */
    public static CompanionSoul load(CompoundTag tag) {
        CompanionSoul soul = new CompanionSoul();
        CompoundTag traits = tag.getCompound("Character");
        for (Trait trait : Trait.values()) {
            int level = traits.getInt(trait.key);
            if (level != 0 && Math.abs(level) <= 2) soul.character.put(trait, level);
        }
        for (Tag text : tag.getList("Own", Tag.TAG_STRING)) {
            if (soul.own.size() < OWN_TOPICS && speakable(text.getAsString()) && !soul.own.contains(text.getAsString())) soul.own.add(text.getAsString());
        }
        for (Tag item : tag.getList("Raised", Tag.TAG_STRING)) {
            String key = item.getAsString();
            if (key.length() <= 200 && (key.startsWith("bank:") || key.startsWith("own:"))) soul.raised.add(key);
        }
        while (soul.raised.size() > RAISED) soul.raised.remove(soul.raised.iterator().next());
        CompoundTag likes = tag.getCompound("Taste");
        for (String category : likes.getAllKeys()) {
            if (category.length() <= 40) soul.taste.put(category, Math.max(-MAX_TASTE, Math.min(MAX_TASTE, likes.getInt(category))));
        }
        return soul;
    }

    private static ListTag strings(Collection<String> values) {
        ListTag list = new ListTag();
        for (String value : values) list.add(StringTag.valueOf(value));
        return list;
    }
}

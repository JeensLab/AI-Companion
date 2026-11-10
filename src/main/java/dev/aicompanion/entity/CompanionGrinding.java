package dev.aicompanion.entity;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Two alike folded into one and enchantments ground off, as a player does it, and only on an order (decided 10.11). «Почини кирку»:
 * the two most worn plain ones of his are folded where he stands by the game's own repair recipe of the crafting grid, which gives
 * what a grindstone gives for a plain thing; one with enchantments, a name, a trim or a dye is left, the grid would drop them. «Сними чары»: the
 * thing is taken to a grindstone a few steps away, or to his own made from his bag, and the experience falls out there.
 */
public final class CompanionGrinding {
    /** The kinds a word names with no material: «почини кирку» is any pickaxe of his. */
    public static final Set<String> KINDS = Set.of("pickaxe", "axe", "sword", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots");
    static final String NO_GRINDSTONE = "Рядом нет точила, а сделать не из чего.";
    private static final Map<String, CompanionItemWords.Forms> KIND_WORDS = Map.of(
            "pickaxe", new CompanionItemWords.Forms("кирка", "кирку", "кирки"), "axe", new CompanionItemWords.Forms("топор", "топор", "топора"),
            "sword", new CompanionItemWords.Forms("меч", "меч", "меча"), "shovel", new CompanionItemWords.Forms("лопата", "лопату", "лопаты"),
            "hoe", new CompanionItemWords.Forms("мотыга", "мотыгу", "мотыги"), "helmet", new CompanionItemWords.Forms("шлем", "шлем", "шлема"),
            "chestplate", new CompanionItemWords.Forms("нагрудник", "нагрудник", "нагрудника"), "leggings", new CompanionItemWords.Forms("поножи", "поножи", "поножей"),
            "boots", new CompanionItemWords.Forms("ботинки", "ботинки", "ботинок"));

    private final CompanionWork w;
    /** The order: what to grind and how many of it; the grindstone he goes to; his own was made or set down already. */
    private String kind = "";
    private int count;
    private BlockPos grindstone;
    private boolean ownTried;

    /** Made with the work, before its owner is set: the owner is read when used. */
    CompanionGrinding(CompanionWork w) { this.w = w; }

    /** A thing of his: in a slot of the bag, or worn or held. */
    private record Held(int slot, EquipmentSlot worn, ItemStack stack) {
        void put(CompanionEntity c, ItemStack value) { if (worn != null) c.setItemSlot(worn, value); else c.setItem(slot, value); }
    }

    private static List<Held> things(CompanionEntity c, String kind) {
        List<Held> found = new ArrayList<>();
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (matches(c, c.getItem(slot), kind)) found.add(new Held(slot, null, c.getItem(slot)));
        for (EquipmentSlot worn : EquipmentSlot.values())
            if (worn != EquipmentSlot.BODY && matches(c, c.getItemBySlot(worn), kind)) found.add(new Held(-1, worn, c.getItemBySlot(worn)));
        return found;
    }

    private static boolean matches(CompanionEntity c, ItemStack stack, String kind) {
        if (stack.isEmpty()) return false;
        return switch (kind) {
            case "pickaxe" -> stack.is(ItemTags.PICKAXES);
            case "axe" -> stack.is(ItemTags.AXES);
            case "sword" -> stack.is(ItemTags.SWORDS);
            case "shovel" -> stack.is(ItemTags.SHOVELS);
            case "hoe" -> stack.is(ItemTags.HOES);
            // By the armor's own type: elytra sit in the chest slot too, and are no chestplate (review 10.11).
            case "helmet" -> stack.getItem() instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.HELMET;
            case "chestplate" -> stack.getItem() instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.CHESTPLATE;
            case "leggings" -> stack.getItem() instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.LEGGINGS;
            case "boots" -> stack.getItem() instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.BOOTS;
            default -> kind.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        };
    }

    /** The words for what the order names: the kind's own, the item's, or its name in quotes. */
    private static CompanionItemWords.Forms words(String kind) {
        CompanionItemWords.Forms forms = KIND_WORDS.get(kind);
        if (forms != null) return forms;
        ResourceLocation id = ResourceLocation.tryParse(kind);
        return id != null && BuiltInRegistries.ITEM.containsKey(id) ? words(BuiltInRegistries.ITEM.get(id)) : new CompanionItemWords.Forms("«" + kind + "»", "«" + kind + "»", "«" + kind + "»");
    }

    private static CompanionItemWords.Forms words(Item item) {
        CompanionItemWords.Forms forms = CompanionItemWords.of(item);
        String name = "«" + CompanionWork.itemName(item) + "»";
        return forms != null ? forms : new CompanionItemWords.Forms(name, name, name);
    }

    /** «кирка», «железный меч»: the order's thing in the task list (a kind like "pickaxe" is no word for the player). */
    public static String label(String kind) { return words(kind).name().replaceAll("^«|»$", ""); }

    private static String capital(String text) { return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1); }

    /** Enchantments a grindstone takes off: all but curses, which the game keeps on a folded or ground thing. */
    private static boolean enchanted(ItemStack stack) {
        return EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().stream().anyMatch(enchantment -> !enchantment.is(EnchantmentTags.CURSE));
    }

    /**
     * The crafting grid's repair makes a new thing of the item (RepairItemRecipe): a name, a trim, a dye or a pattern is lost, as
     * enchantments are. Such a thing is not folded in his hands; only wear and the work cost are no loss.
     */
    private static boolean plain(ItemStack stack) {
        // Curses the grid keeps (RepairItemRecipe takes the higher level), so a cursed one is folded too.
        return stack.getComponentsPatch().entrySet().stream().map(java.util.Map.Entry::getKey)
                .allMatch(type -> type == DataComponents.DAMAGE || type == DataComponents.REPAIR_COST || type == DataComponents.ENCHANTMENTS && !enchanted(stack));
    }

    /** Where a folded thing stays: the main hand first, then what he wears or holds, then the bag. */
    private static int rank(Held held) { return held.worn == EquipmentSlot.MAINHAND ? 0 : held.worn != null ? 1 : 2; }

    private static int remaining(ItemStack stack) { return stack.getMaxDamage() - stack.getDamageValue(); }

    /**
     * «Почини»: the two most worn plain ones of a kind folded into one, again while a pair fits into one whole without loss; the first
     * pair of the most worn kind always (the player asked), the loss said. The result stays where one of the two was worn or held.
     */
    static CompanionActionResult repair(CompanionEntity c, String kind) {
        if (!(c.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Не могу чинить без загруженного мира.");
        CompanionItemWords.Forms asked = words(kind);
        List<Held> all = things(c, kind).stream().filter(held -> held.stack.isDamageableItem() && held.stack.getCount() == 1).toList();
        if (all.isEmpty()) return c.recordActionResult(CompanionActionResult.failed("У меня нет " + asked.genitive() + " — чинить нечего. Положи мне в сумку две одинаковые — сложу."));
        Map<Item, List<Held>> plain = new LinkedHashMap<>();
        for (Held held : all) if (plain(held.stack)) plain.computeIfAbsent(held.stack.getItem(), item -> new ArrayList<>()).add(held);
        // Two worn ones at least: a whole one folded with another is the whole one again, the other only thrown away (review 10.11).
        Map<Item, List<Held>> pairs = new LinkedHashMap<>(plain);
        pairs.values().removeIf(group -> group.stream().filter(held -> held.stack.isDamaged()).count() < 2);
        if (pairs.isEmpty()) {
            Held special = all.stream().filter(held -> !plain(held.stack)).findFirst().orElse(null);
            boolean pair = special != null && all.stream().filter(held -> held.stack.is(special.stack.getItem())).count() >= 2;
            List<Held> alike = plain.values().stream().filter(group -> group.size() >= 2).findFirst().orElse(null);
            return c.recordActionResult(CompanionActionResult.failed(pair && enchanted(special.stack)
                    ? capital(words(special.stack.getItem()).name()) + " с чарами — при починке чары пропадут. Если не жалко, скажи «сними чары»."
                    : pair ? capital(words(special.stack.getItem()).name()) + " — особенная: при починке в руках пропадёт то, что на ней сверх износа (имя, отделка, краска, заряд или метка)."
                    : alike != null && alike.stream().noneMatch(held -> held.stack.isDamaged()) ? "Чинить нечего: " + words(alike.getFirst().stack.getItem()).name() + " без износа."
                    : alike != null ? "Чинить нечего: изношенная вещь одна, а сложить её с целой — значит выбросить целую."
                    : "Сложить не с чем: нужна пара одинаковых, а " + words(all.getFirst().stack.getItem()).name() + " у меня без пары."));
        }
        // The kind the player means: the one with the most worn thing.
        Item first = pairs.entrySet().stream().min(Comparator.comparingDouble(entry -> entry.getValue().stream()
                .mapToDouble(held -> remaining(held.stack) / (double) held.stack.getMaxDamage()).min().orElse(1))).orElseThrow().getKey();
        StringBuilder report = new StringBuilder();
        ItemStack shown = ItemStack.EMPTY;
        List<Item> order = new ArrayList<>(pairs.keySet());
        order.remove(first); order.addFirst(first);
        for (Item item : order) {
            List<Held> group = new ArrayList<>(pairs.get(item));
            boolean firstPair = item == first, groupStart = true;
            while (group.size() >= 2) {
                group.sort(Comparator.comparingInt(held -> remaining(held.stack)));
                Held a = group.get(0), b = group.get(1);
                int max = Math.max(a.stack.getMaxDamage(), b.stack.getMaxDamage());
                int loss = remaining(a.stack) + remaining(b.stack) + max * 5 / 100 - max;
                if (loss > 0 && !firstPair || !b.stack.isDamaged()) break;
                CraftingInput input = CraftingInput.of(2, 1, List.of(a.stack, b.stack));
                ItemStack folded = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                        .map(recipe -> recipe.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
                if (folded.isEmpty() || !folded.is(a.stack.getItem())) break;
                // Where one of the two is worn or held, the folded one goes there; the other place is emptied.
                Held keep = rank(b) < rank(a) ? b : a, gone = keep == a ? b : a;
                gone.put(c, ItemStack.EMPTY); keep.put(c, folded);
                group.remove(gone); group.set(group.indexOf(keep), new Held(keep.slot, keep.worn, folded));
                report.append(report.isEmpty() ? "Починил " + words(item).accusative() + ": сложил две в одну"
                                : groupStart ? " И " + words(item).accusative() : " И ещё одну")
                        .append(loss > 0 ? " — целая, " + loss + " прочности пропало." : ", теперь " + remaining(folded) + " из " + folded.getMaxDamage() + ".");
                firstPair = groupStart = false; shown = folded;
            }
        }
        if (report.isEmpty()) return c.recordActionResult(CompanionActionResult.failed("Не получилось сложить " + words(first).accusative() + " — так не чинится."));
        c.animateAction(2, c.blockPosition(), shown);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "items_repaired", "entity", c.getUUID(), "kind", kind, "report", report.toString());
        c.refreshInventorySummary();
        return c.recordActionResult(CompanionActionResult.ok(report.toString()));
    }

    /** «Сними чары»: null when the work may start, else why not. */
    CompanionActionResult begin(String kind, int count) {
        CompanionEntity c = w.owner;
        this.kind = kind; this.count = Math.max(1, count); grindstone = null; ownTried = false;
        if (!(c.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Не могу без загруженного мира.");
        List<Held> things = things(c, kind);
        if (things.stream().noneMatch(held -> enchanted(held.stack))) {
            boolean cursed = things.stream().anyMatch(held -> !EnchantmentHelper.getEnchantmentsForCrafting(held.stack).isEmpty());
            return CompanionActionResult.failed(cursed ? "У " + words(kind).genitive() + " только проклятие — точилом его не снять."
                    : "У меня нет " + words(kind).genitive() + " с чарами. Положи мне в сумку — сниму.");
        }
        grindstone = c.placement.nearest(Blocks.GRINDSTONE, CompanionToolSense.BENCH_WALK);
        if (grindstone == null && !carries(Items.GRINDSTONE)
                && !CompanionCrafting.plan(level, c.companionInventory, "minecraft:grindstone", 1, true).success()) return CompanionActionResult.failed(NO_GRINDSTONE);
        return null;
    }

    String jobName() { return "снятие чар с " + words(kind).genitive(); }

    void tick(ServerLevel level) {
        CompanionEntity c = w.owner;
        BlockPos here = c.placement.inReach(Blocks.GRINDSTONE);
        if (here != null) { grind(level, here); return; }
        if (grindstone != null && !level.getBlockState(grindstone).is(Blocks.GRINDSTONE)) grindstone = c.placement.nearest(Blocks.GRINDSTONE, CompanionToolSense.BENCH_WALK);
        if (grindstone == null) {
            if (ownTried) w.finish(false, NO_GRINDSTONE); else own(level);
            return;
        }
        // In the air no way is searched (as to a bench); in water he goes on (as to the stonecutter).
        if (!c.onGround() && !c.isInWater()) return;
        if (!grindstone.equals(w.heading)) { w.heading = grindstone; w.resetApproach(); }
        c.movementIntent(grindstone);
        if (w.approach(level, grindstone, 2.8D)) { c.workStatus("Иду к точилу на " + grindstone.toShortString() + "."); return; }
        w.finish(false, "Не смог подойти к точилу на " + grindstone.toShortString() + ".");
    }

    /** His own grindstone, from the bag only (the order counted on it): made at a bench, set down beside him and left standing. */
    private void own(ServerLevel level) {
        CompanionEntity c = w.owner;
        if (!carries(Items.GRINDSTONE)) {
            if (w.crafting.approachCraftingTable()) return;
            if (!c.craftFromBag("minecraft:grindstone", 1).success()) { ownTried = true; w.finish(false, NO_GRINDSTONE); return; }
        }
        ownTried = true;
        BlockPos at = c.placement.findPlacementPosition(level, Blocks.GRINDSTONE, c.blockPosition(), pos -> true);
        ItemStack item = c.companionInventory.stream().filter(stack -> stack.is(Items.GRINDSTONE)).findFirst().orElse(ItemStack.EMPTY);
        if (at == null || item.isEmpty()) { w.finish(false, "Точило у меня есть, а поставить его рядом некуда."); return; }
        level.setBlock(at, Blocks.GRINDSTONE.defaultBlockState().setValue(GrindstoneBlock.FACE, AttachFace.FLOOR).setValue(GrindstoneBlock.FACING, c.getDirection()), 3);
        c.animateAction(1, at, item);
        c.rememberPlaced(at);
        item.shrink(1);
        c.refreshInventorySummary();
        level.playSound(null, at, Blocks.GRINDSTONE.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, .7F, 1F);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "block_placed", "entity", c.getUUID(), "block", "minecraft:grindstone", "position", at, "reason", "disenchant");
        grindstone = at;
        c.workStatus("Поставил своё точило.");
    }

    private boolean carries(Item item) { return w.owner.companionInventory.stream().anyMatch(stack -> stack.is(item)); }

    /** At the grindstone: the cheapest enchanted ones first (losing Infinity hurts more than Power I), the experience at the stone. */
    private void grind(ServerLevel level, BlockPos at) {
        CompanionEntity c = w.owner;
        List<Held> enchanted = things(c, kind).stream().filter(held -> enchanted(held.stack)).sorted(Comparator.comparingInt(held -> experience(held.stack))).toList();
        if (enchanted.isEmpty()) { w.finish(false, "У меня больше нет " + words(kind).genitive() + " с чарами."); return; }
        List<String> names = new ArrayList<>();
        boolean cursed = false, experience = false;
        ItemStack shown = ItemStack.EMPTY;
        for (Held held : enchanted.subList(0, Math.min(count, enchanted.size()))) {
            int points = experience(held.stack);
            ItemStack ground = groundOff(held.stack.copy());
            held.put(c, ground);
            names.add(words(held.stack.getItem()).genitive());
            cursed |= !EnchantmentHelper.getEnchantmentsForCrafting(ground).isEmpty();
            // As the grindstone pays a player on taking the result: half to the whole of the enchantments' worth.
            if (points > 0) {
                int half = (int) Math.ceil(points / 2.0);
                ExperienceOrb.award(level, Vec3.atCenterOf(at), half + level.random.nextInt(half));
                experience = true;
            }
            level.levelEvent(1042, at, 0);
            shown = ground;
        }
        c.animateAction(1, at, shown);
        c.refreshInventorySummary();
        Map<String, Integer> counted = new LinkedHashMap<>();
        for (String one : names) counted.merge(one, 1, Integer::sum);
        List<String> said = counted.entrySet().stream().map(entry -> entry.getKey() + (entry.getValue() > 1 ? " (" + entry.getValue() + " шт.)" : "")).toList();
        String message = "Снял чары на точиле с " + String.join(", ", said) + "." + (experience ? " Опыт лежит у точила." : "")
                + (cursed ? " Проклятие точилом не снять — осталось." : "");
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "items_disenchanted", "entity", c.getUUID(), "kind", kind, "count", names.size(), "grindstone", at);
        w.finish(CompanionActionResult.ok(message)
                .withEvidence(new CompanionActionResult.Evidence(w.taskId, "disenchant_item", kind, names.size(), c.getUUID(), null, null)));
    }

    /** The worth of the enchantments a grindstone takes off: their least cost, curses not counted. */
    private static int experience(ItemStack stack) {
        int points = 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet())
            if (!entry.getKey().is(EnchantmentTags.CURSE)) points += entry.getKey().value().getMinCost(entry.getIntValue());
        return points;
    }

    /** The grindstone's result for one thing (GrindstoneMenu.removeNonCursesFrom): curses stay, a bare book is a book again. */
    private static ItemStack groundOff(ItemStack item) {
        ItemEnchantments left = EnchantmentHelper.updateEnchantments(item, enchantments -> enchantments.removeIf(enchantment -> !enchantment.is(EnchantmentTags.CURSE)));
        if (item.is(Items.ENCHANTED_BOOK) && left.isEmpty()) item = item.transmuteCopy(Items.BOOK);
        int cost = 0;
        for (int i = 0; i < left.size(); i++) cost = AnvilMenu.calculateIncreasedRepairCost(cost);
        item.set(DataComponents.REPAIR_COST, cost);
        return item;
    }
}

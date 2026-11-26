package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionItemLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * An order for a thing by name is work, never a sentence (owner, 24.11: «добудь тыкву» got «Ага сейчас сорву» and nothing was
 * done at all — no way_search line, no step, no item). Every phrase here goes through the player's own router, exactly as the
 * chat screen routes it, and ends with the thing in the bag. The router answers by the game's own registry
 * ({@link CompanionItemLookup}), never by a list of names in this test: a gourd, a storage block, an ore's block, a plain
 * block, a plant and a mod's own thing are the same order, and only a name the registry cannot answer is left to the model.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionItemOrderTests {
    private static final int SIDE = 48, TOP = 24, GROUND = 5, FEET = GROUND + 1;
    private static final int HOME_X = 20, HOME_Z = 16;

    // ---- the order reaches the obtain logic: the router answers with a work step, not null ----

    /** «Добудь тыкву», «добудь 1 тыкву», «добудь блок тыквы», «сорви тыкву»: one obtain step each, one pumpkin. */
    @GameTest(template = "empty")
    public static void aGourdOrderIsAWorkStep(GameTestHelper h) {
        for (String phrase : List.of("добудь тыкву", "добудь 1 тыкву", "добудь блок тыквы", "сорви тыкву", "нарви тыкву",
                "Олег, добудь тыкву", "добудь тыкву рядом")) {
            step(h, phrase, "minecraft:pumpkin", 1);
        }
        h.succeed();
    }

    /** Every kind by its own name: a storage block, a plain block, glass, wool, a plant, a barrel, planks — one obtain step each. */
    @GameTest(template = "empty")
    public static void everyKindIsAnObtainStep(GameTestHelper h) {
        step(h, "добудь блок лазурита", "minecraft:lapis_block", 1);
        step(h, "добудь золотой блок", "minecraft:gold_block", 1);
        step(h, "добудь стекло", "minecraft:glass", 1);
        step(h, "добудь белую шерсть", "minecraft:white_wool", 1);
        step(h, "сорви мак", "minecraft:poppy", 1);
        step(h, "добудь бочку", "minecraft:barrel", 1);
        step(h, "добудь 3 дубовые доски", "minecraft:oak_planks", 3);
        step(h, "добудь 2 блока лазурита", "minecraft:lapis_block", 2);
        h.succeed();
    }

    /** «Принеси мне 2 тыквы»: got and handed over — the same order, with the hand-over step after it. */
    @GameTest(template = "empty")
    public static void aCarryOrderEndsWithTheHandOver(GameTestHelper h) {
        String command = CompanionLocalCommands.route("принеси мне 2 тыквы", "Олег", RU);
        h.assertTrue(command != null, "«принеси мне 2 тыквы» went to the model");
        var actions = CompanionLocalCommands.actions(command);
        h.assertTrue(actions.size() == 2 && actions.getFirst().type().equals("obtain_item")
                        && actions.getFirst().count() == 2 && actions.getFirst().item().equals("minecraft:pumpkin"),
                "The carry order is not an obtain of two pumpkins: " + actions);
        h.assertTrue(actions.getLast().type().equals("give_items") && actions.getLast().item().equals("minecraft:pumpkin"),
                "The carry order does not end with the hand-over: " + actions);
        h.succeed();
    }

    /**
     * The orders that stay the model's: a compound phrase («и вернись»), a name the registry cannot answer («кура»), and the
     * orders the specific rules own («добудь обсидиан» keeps its ten, «добудь 2 железа» its ore). A local guess here would
     * lose the steps of the phrase or dig the wrong thing.
     */
    @GameTest(template = "empty")
    public static void onlyThePlainOrdersOfAKnownThingAreTaken(GameTestHelper h) {
        for (String phrase : List.of("добудь тыкву и вернись", "добудь тыкву потом домой", "принеси куру", "добудь тухлое мясо")) {
            h.assertTrue(CompanionLocalCommands.route(phrase, "Олег", RU) == null, "«" + phrase + "» was taken by the item order");
        }
        h.assertTrue("companion work obtain_item 10 minecraft:obsidian".equals(CompanionLocalCommands.route("добудь обсидиан", "Олег", RU)),
                "«добудь обсидиан» lost its ten");
        h.assertTrue("companion work mine_ore 2 minecraft:iron_ore".equals(CompanionLocalCommands.route("добудь 2 железа", "Олег", RU)),
                "«добудь 2 железа» lost its ore");
        h.assertTrue(CompanionLocalCommands.route("добудь лазурит", "Олег", RU).startsWith("companion work mine_ore"),
                "«добудь лазурит» lost the ore's own work");
        h.assertTrue("companion work gather_stone 4 resources".equals(CompanionLocalCommands.route("добудь 4 булыжника", "Олег", RU)),
                "«добудь 4 булыжника» lost the stone order");
        h.assertTrue("companion work gather_stone 10 resources".equals(CompanionLocalCommands.route("добудь 10 камня", "Олег", RU)),
                "«добудь 10 камня» lost its ten");
        h.assertTrue("companion work gather_stone 8 resources".equals(CompanionLocalCommands.route("добудь камня", "Олег", RU)),
                "«добудь камня» lost its eight");
        h.assertTrue(CompanionLocalCommands.route("принеси воды", "Олег", RU).startsWith("companion work obtain_item 1 minecraft:water_bucket"),
                "«принеси воды» lost its water order");
        h.succeed();
    }

    /** The order the model answers with words only says so in the log, with the thing the registry names — and a talk is no line. */
    @GameTest(template = "empty")
    public static void anOrderWithoutAStepSaysWhy(GameTestHelper h) {
        CompanionPlanRequirements.apply("добудь тыкву", new CompanionReply("Ага сейчас сорву", List.of()));
        CompanionPlanRequirements.apply("привет, как дела", new CompanionReply("Хорошо", List.of()));
        CompanionPlanRequirements.apply("Как думаешь, что лучше построить?", new CompanionReply("Дом", List.of()));
        List<String> lines = journal("order_no_step", "добудь тыкву");
        h.assertTrue(!lines.isEmpty(), "An order without a step was not said: " + lines);
        String line = lines.getLast();
        h.assertTrue(line.contains("добудь тыкву") && line.contains("шага нет"), "The line does not name the order or say there was no step: " + line);
        h.assertTrue(line.contains("Ага сейчас сорву"), "The line does not say what was said: " + line);
        h.assertTrue(line.contains("\"reply\":\"Ага сейчас сорву\""), "The line does not carry the reply as a field: " + line);
        // The thing is named by the game's registry: on this server the names are en_us, so an English name proves the branch.
        CompanionPlanRequirements.apply("добудь pumpkin", new CompanionReply("Сейчас", List.of()));
        List<String> named = journal("order_no_step", "добудь pumpkin");
        h.assertTrue(!named.isEmpty() && named.getLast().contains("minecraft:pumpkin")
                && named.getLast().contains("\"why\":\"предмет назван, а шага нет\""), "A named thing without a step is not said as such: " + named);
        // A name the game cannot answer: the reason is the name itself.
        CompanionPlanRequirements.apply("добудь куру", new CompanionReply("Не понял", List.of()));
        List<String> unknown = journal("order_no_step", "добудь куру");
        h.assertTrue(!unknown.isEmpty() && unknown.getLast().contains("имени нет в игре"), "An unknown name is not said as such: " + unknown);
        h.succeed();
    }

    // ---- the order is done, not said ----

    /**
     * His own scene: «добудь тыкву», «добудь 1 тыкву», «добудь блок тыквы» one after another, each answered by the router and
     * run by the plan runner. Every one ends with a pumpkin in the bag and its own way_search line — the work really ran.
     */
    @GameTest(template = "way_wide", timeoutTicks = 24000)
    public static void everyGourdOrderIsDoneNotSaid(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 32));
        List<String> phrases = List.of("добудь тыкву", "добудь 1 тыкву", "добудь блок тыквы");
        int[] at = {0};
        java.util.concurrent.atomic.AtomicReference<CompletableFuture<CompanionPlanRunner.Outcome>> future = new java.util.concurrent.atomic.AtomicReference<>();
        h.onEachTick(() -> {
            if (at[0] >= phrases.size()) return;
            if (future.get() == null) {
                BlockPos target = new BlockPos(HOME_X + 3 + at[0], FEET + 4, HOME_Z + 2);
                h.setBlock(target, Blocks.PUMPKIN);
                future.set(say(h, c, phrases.get(at[0])));
                return;
            }
            if (!future.get().isDone()) return;
            String report = future.get().isCompletedExceptionally() ? "exception" : future.get().join().report();
            h.assertTrue(count(c, Items.PUMPKIN) >= 1, "«" + phrases.get(at[0]) + "» left no pumpkin in the bag: " + report);
            h.assertTrue(!report.contains("С этим пока не справился"), "The old refusal came back: " + report);
            // The next order must not be answered by this pumpkin: the bag keeps only the soil and the pickaxe.
            for (int slot = 0; slot < c.getContainerSize(); slot++) {
                ItemStack stack = c.getItem(slot);
                if (!stack.is(Items.DIRT) && !stack.is(Items.DIAMOND_PICKAXE)) c.setItem(slot, ItemStack.EMPTY);
            }
            future.set(null);
            at[0]++;
        });
        h.succeedWhen(() -> {
            h.assertTrue(at[0] >= phrases.size(), "Still on «" + phrases.get(Math.min(at[0], phrases.size() - 1)) + "»: " + c.getLastTaskResult());
            List<String> lines = journal(c, "way_search");
            h.assertTrue(lines.stream().anyMatch(line -> line.contains("\"item\":\"minecraft:pumpkin\"") && line.contains("\"chosen\":\"")),
                    "No way_search line naming the chosen pumpkin: " + lines);
        });
    }

    /** «Добудь бочку» for a barrel the player hung himself: the order is the work, and the barrel is in the bag. */
    @GameTest(template = "way_wide", timeoutTicks = 16000)
    public static void aPlainBlockOrderIsWorkToo(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        BlockPos target = new BlockPos(HOME_X + 5, FEET + 6, HOME_Z + 3);
        placedByPlayer(h, c, target, Blocks.BARREL);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 32));
        var future = say(h, c, "добудь бочку");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.BARREL) >= 1, "The barrel is not in the bag: " + future.join().report());
        });
    }

    // ---- helpers ----

    /** The step the router answers with, and its item and count: the order is a work step of the obtain path, not a sentence. */
    private static void step(GameTestHelper h, String phrase, String id, int count) {
        String command = CompanionLocalCommands.route(phrase, "Олег", RU);
        h.assertTrue(command != null, "«" + phrase + "» went to the model instead of the obtain logic");
        h.assertTrue(command.startsWith("companion work obtain_item "), "«" + phrase + "» is not an obtain: " + command);
        var actions = CompanionLocalCommands.actions(command);
        PlannedAction action = actions.getFirst();
        h.assertTrue(action.type().equals("obtain_item"), "«" + phrase + "» is not an obtain step: " + actions);
        h.assertTrue(action.item().equals(id), "«" + phrase + "» names " + action.item() + ", not " + id);
        h.assertTrue(action.count() == count, "«" + phrase + "» asks " + action.count() + ", not " + count);
        h.assertTrue(!CompanionLocalCommands.cancelsPlans(command), "A work order cancels the queue: " + command);
    }

    /** The player's phrase through the router and the runner: a local order, no model. */
    private static CompletableFuture<CompanionPlanRunner.Outcome> say(GameTestHelper h, CompanionEntity c, String phrase) {
        String command = CompanionLocalCommands.route(phrase, "Олег", RU);
        h.assertTrue(command != null, "«" + phrase + "» went to the model");
        return CompanionPlanRunner.run(CompanionLocalCommands.actions(command),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-item-order", () -> false));
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), HOME_X, FEET, HOME_Z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(HOME_X - 2, FEET, HOME_Z));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(32);
        c.setLifestyle(false, false);
        return c;
    }

    private static void flat(GameTestHelper h, Block floor) {
        for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++) for (int y = 0; y < TOP; y++) {
            boolean border = x == 0 || x == SIDE - 1 || z == 0 || z == SIDE - 1;
            if (y == 0 || border) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
            else if (y == GROUND) h.setBlock(new BlockPos(x, y, z), floor);
            else if (y < GROUND) h.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
            else h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    /** A real placement by the player: the event the game fires, the bookkeeping the mod keeps (CompanionEvents.onBlockPlaced). */
    private static void placedByPlayer(GameTestHelper h, CompanionEntity c, BlockPos cell, Block block) {
        h.setBlock(cell, block);
        var player = (net.minecraft.server.level.ServerPlayer) h.getLevel().getPlayerByUUID(c.getOwnerId());
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                net.neoforged.neoforge.common.util.BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), h.absolutePos(cell)),
                Blocks.GRASS_BLOCK.defaultBlockState(), player));
    }

    /** The journalled lines of this companion with this event name. */
    private static List<String> journal(CompanionEntity c, String event) {
        return journal(event, c.getUUID().toString());
    }

    /** The journalled lines with this event name that name this text (the companion's id or the order itself). */
    private static List<String> journal(String event, String named) {
        dev.aicompanion.CompanionDiagnostics.flush();
        var directory = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("logs").resolve("aicompanion");
        List<String> lines = new ArrayList<>();
        for (String name : List.of("companion-3.jsonl", "companion-2.jsonl", "companion-1.jsonl", "companion-latest.jsonl")) {
            try { lines.addAll(java.nio.file.Files.readAllLines(directory.resolve(name))); } catch (java.io.IOException | java.io.UncheckedIOException ignored) { }
        }
        return lines.stream().filter(line -> line.contains(named) && line.contains("\"event\":\"" + event + "\"")).toList();
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (c.getItemBySlot(slot).is(item)) count += c.getItemBySlot(slot).getCount();
        return count;
    }

    /**
     * The names a Russian client shows, hand-picked here because the GameTest server runs en_us. They stand in for the
     * player's language: the resolver is the same one the client runs, only the names come from here.
     */
    private static final java.util.Map<String, String> RU_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry("pumpkin", "Тыква"),
            java.util.Map.entry("carved_pumpkin", "Вырезанная тыква"),
            java.util.Map.entry("lapis_block", "Лазуритовый блок"),
            java.util.Map.entry("lapis_lazuli", "Лазурит"),
            java.util.Map.entry("gold_block", "Золотой блок"),
            java.util.Map.entry("cobblestone", "Булыжник"),
            java.util.Map.entry("glass", "Стекло"),
            java.util.Map.entry("white_wool", "Белая шерсть"),
            java.util.Map.entry("poppy", "Мак"),
            java.util.Map.entry("barrel", "Бочка"),
            java.util.Map.entry("oak_planks", "Дубовые доски"),
            java.util.Map.entry("obsidian", "Обсидиан"),
            java.util.Map.entry("iron_ore", "Железная руда"),
            java.util.Map.entry("water_bucket", "Ведро воды"));

    private static final Function<Item, String> RU = CompanionItemOrderTests::russianName;

    private static String russianName(Item item) {
        String name = RU_NAMES.get(BuiltInRegistries.ITEM.getKey(item).getPath());
        return name != null ? name : new ItemStack(item).getHoverName().getString();
    }
}

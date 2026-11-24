package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
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

/**
 * The owner's own blocks (idea PATH7, #105): he builds his base himself — a chest and the walls around it are set down by him — and
 * then hangs the block he wants in the air above it, three to ten up and two to eight across, with nothing under it. Every GameTest
 * before this one set its blocks with the harness, so the bookkeeping of what the player builds never saw them: in his world it did,
 * and the one search threw every block he asked for out as «the player's build» — «блоков не нашёл» over blocks he could see, and
 * «Рядом не вижу, где взять …» over a pumpkin he had hung himself. These rows place and record the blocks exactly as the game does
 * (a real place event from a real player, CompanionPlayerBuilds) and then ask for them by the phrase: a gourd, a storage block, an
 * ore's item and a plain block. A block the player asked for by name is his to take even when he set it down himself; what «do not
 * break the player's build» keeps is the soil the way lays under his feet, never the target of the order.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWayPlayerBuildTests {
    private static final int SIDE = 24, TOP = 18, GROUND = 4, FEET = GROUND + 1;
    private static final int HOME_X = 12, HOME_Z = 12;

    // ---- one kind each: hung above the base he built, asked for by the phrase ----

    /** A pumpkin he hung five up and four across his base: «добудь тыкву» takes it, though it stands on his own building. */
    @GameTest(template = "way_far", timeoutTicks = 16000)
    public static void aGourdHeHungAboveHisBaseIsTaken(GameTestHelper h) {
        taken(h, Blocks.PUMPKIN, Items.PUMPKIN, 4, 2, 5, "добудь тыкву", "minecraft:pumpkin");
    }

    /** A lapis block he hung seven up and six across his base: «добудь блок лазурита» takes it as it lies. */
    @GameTest(template = "way_far", timeoutTicks = 16000)
    public static void aStorageBlockHeHungAboveHisBaseIsTaken(GameTestHelper h) {
        taken(h, Blocks.LAPIS_BLOCK, Items.LAPIS_BLOCK, 6, 3, 7, "добудь блок лазурита", "minecraft:lapis_block");
    }

    /** «Добудь лазурит» over the lapis block he hung: the block is not an ore, it is the block that makes the item — it is mined. */
    @GameTest(template = "way_far", timeoutTicks = 16000)
    public static void anOresItemFromTheBlockHeHungIsTaken(GameTestHelper h) {
        taken(h, Blocks.LAPIS_BLOCK, Items.LAPIS_LAZULI, 3, 4, 4, "добудь лазурит", "minecraft:lapis_lazuli");
    }

    /** A plain block he hung ten up and eight across his base: «добудь булыжник» takes the block itself, not a stone face. */
    @GameTest(template = "way_far", timeoutTicks = 16000)
    public static void aPlainBlockHeHungAboveHisBaseIsTaken(GameTestHelper h) {
        taken(h, Blocks.COBBLESTONE, Items.COBBLESTONE, 8, 2, 10, "добудь булыжник", "minecraft:cobblestone");
    }

    /** His whole scene: four blocks of different kinds hung above his base at once, asked one after another — every one is taken. */
    @GameTest(template = "way_far", timeoutTicks = 40000)
    public static void hisWholeSceneOfHungBlocksIsTaken(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        base(h, c);
        List<Block> blocks = List.of(Blocks.PUMPKIN, Blocks.LAPIS_BLOCK, Blocks.COBBLESTONE, Blocks.GOLD_BLOCK);
        List<Item> items = List.of(Items.PUMPKIN, Items.LAPIS_BLOCK, Items.COBBLESTONE, Items.GOLD_BLOCK);
        List<String> ids = List.of("minecraft:pumpkin", "minecraft:lapis_block", "minecraft:cobblestone", "minecraft:gold_block");
        List<String> names = List.of("добудь тыкву", "добудь блок лазурита", "добудь булыжник", "добудь золотой блок");
        List<BlockPos> targets = List.of(new BlockPos(HOME_X + 3, FEET + 3, HOME_Z + 1), new BlockPos(HOME_X + 5, FEET + 6, HOME_Z + 3),
                new BlockPos(HOME_X + 7, FEET + 9, HOME_Z + 2), new BlockPos(HOME_X + 8, FEET + 10, HOME_Z + 4));
        for (int at = 0; at < blocks.size(); at++) hung(h, c, targets.get(at), blocks.get(at));
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 32));
        List<CompletableFuture<CompanionPlanRunner.Outcome>> futures = new ArrayList<>(java.util.Collections.nCopies(ids.size(), (CompletableFuture<CompanionPlanRunner.Outcome>) null));
        int[] at = {0};
        h.onEachTick(() -> {
            if (at[0] >= ids.size()) return;
            if (futures.get(at[0]) == null) { futures.set(at[0], sayModel(h, c, names.get(at[0]), obtainPlan(ids.get(at[0])))); return; }
            if (!futures.get(at[0]).isDone()) return;
            String report = futures.get(at[0]).isCompletedExceptionally() ? "exception" : futures.get(at[0]).join().report();
            h.assertTrue(count(c, items.get(at[0])) >= 1, "«" + names.get(at[0]) + "» of his hung scene is not in the bag: " + report);
            h.assertTrue(!report.contains("Рядом нечего копать") && !report.contains("Рядом не вижу"), "The old refusal is back: " + report);
            // The next order must not be answered by this block's drop: the bag keeps only the soil and the pickaxe.
            for (int slot = 0; slot < c.getContainerSize(); slot++) {
                ItemStack stack = c.getItem(slot);
                if (!stack.is(Items.DIRT) && !stack.is(Items.DIAMOND_PICKAXE)) c.setItem(slot, ItemStack.EMPTY);
            }
            at[0]++;
        });
        h.succeedWhen(() -> {
            h.assertTrue(at[0] >= ids.size(), "Still on block " + at[0] + " of " + ids.size() + ": " + c.getLastTaskResult());
            for (BlockPos target : targets) h.assertBlockPresent(Blocks.AIR, target);
        });
    }

    // ---- the line the player reads ----

    /**
     * The search line of a pumpkin he hung says where it looked and what it threw out: how many kinds of block give the item, how
     * many cells held one, how many of those were the player's own build — and that they were taken anyway, because they are the
     * order. One line, as every search has: the player reads the decision, not only its result.
     */
    @GameTest(template = "way_far", timeoutTicks = 16000)
    public static void theSearchLineSaysTheBlocksWereHisOwnAndTaken(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        base(h, c);
        BlockPos target = new BlockPos(HOME_X + 4, FEET + 5, HOME_Z + 2);
        hung(h, c, target, Blocks.PUMPKIN);
        var future = sayModel(h, c, "добудь тыкву", obtainPlan("minecraft:pumpkin"));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "The pumpkin is not in the bag: " + future.join().report());
            List<String> lines = journal(c, "way_search");
            String line = lines.stream().filter(entry -> entry.contains("\"item\":\"minecraft:pumpkin\"")
                    && entry.contains("\"chosen\":\"")).findFirst().orElse(null);
            h.assertTrue(line != null, "No way_search line naming the chosen pumpkin: " + lines);
            h.assertTrue(line.contains("коробка 32 вбок, 32 вверх и 32 вниз"), "The line does not say the box in words: " + line);
            h.assertTrue(line.contains("видов ") && line.contains("клеток "), "The line does not say how many kinds and cells it saw: " + line);
            h.assertTrue(line.contains("своих "), "The line does not say how many of the blocks were the player's own: " + line);
            h.assertTrue(line.contains("\"kinds\":") && line.contains("\"cells\":") && line.contains("\"own\":"), "The fields of the stages are missing: " + line);
            h.assertBlockPresent(Blocks.AIR, target);
        });
    }

    // ---- helpers ----

    /** A block he hung {@code up} above his base and {@code dx}, {@code dz} across it, nothing under it: asked for and taken. */
    private static void taken(GameTestHelper h, Block block, Item item, int dx, int dz, int up, String phrase, String id) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        base(h, c);
        BlockPos target = new BlockPos(HOME_X + dx, FEET + up, HOME_Z + dz);
        hung(h, c, target, block);
        h.assertBlockPresent(Blocks.AIR, target.below());
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 32));
        var seen = c.obtainAvailable(id);
        h.assertTrue(seen.some(), "The search does not see «" + id + "» the player hung " + up + " up and " + dx + " across his own base: "
                + seen + ", feet " + c.blockPosition() + ", block " + h.absolutePos(target));
        var plan = c.obtainPlan(id, 1);
        h.assertTrue(!plan.refused(), "The plan refuses «" + id + "» he hung himself: " + plan.refusal());
        var future = sayModel(h, c, phrase, obtainPlan(id));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, item) >= 1, "«" + phrase + "» of the block he hung " + up + " up is not in the bag: " + report);
            h.assertTrue(!report.contains("Рядом нечего копать") && !report.contains("Рядом не вижу"), "The old refusal is back: " + report);
            h.assertBlockPresent(Blocks.AIR, target);
        });
    }

    /** His base: a chest and two blocks of his own, recorded by the game's own bookkeeping (a real place event of a real player). */
    private static void base(GameTestHelper h, CompanionEntity c) {
        placedByPlayer(h, c, new BlockPos(HOME_X + 2, FEET, HOME_Z + 2), Blocks.CHEST);
        placedByPlayer(h, c, new BlockPos(HOME_X + 1, FEET, HOME_Z + 2), Blocks.OAK_PLANKS);
        placedByPlayer(h, c, new BlockPos(HOME_X + 3, FEET, HOME_Z + 2), Blocks.OAK_PLANKS);
    }

    /** A block he set down himself in the air, recorded as the game records it: the harness never sees a player's own build. */
    private static void hung(GameTestHelper h, CompanionEntity c, BlockPos cell, Block block) {
        placedByPlayer(h, c, cell, block);
    }

    /** A real placement by the player: the event the game fires, the bookkeeping the mod keeps (CompanionEvents.onBlockPlaced). */
    private static void placedByPlayer(GameTestHelper h, CompanionEntity c, BlockPos cell, Block block) {
        h.setBlock(cell, block);
        var player = (net.minecraft.server.level.ServerPlayer) h.getLevel().getPlayerByUUID(c.getOwnerId());
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                net.neoforged.neoforge.common.util.BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), h.absolutePos(cell)),
                Blocks.GRASS_BLOCK.defaultBlockState(), player));
    }

    private static String obtainPlan(String item) {
        return "[{\"action\":\"obtain_item\",\"item\":\"" + item + "\",\"count\":1}]";
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> sayModel(GameTestHelper h, CompanionEntity c, String phrase, String json) {
        var reply = CompanionPlanRequirements.apply(phrase, new CompanionReply("", CompanionPlanCodec.parsePlan(JsonParser.parseString(json))));
        return CompanionPlanRunner.run(reply.actions(),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-way-player-build-model", () -> false));
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
        // The box of a companion whose search is not cut down: thirty-two across, as the player's own default.
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

    /** The journalled lines of this companion with this event name. */
    private static List<String> journal(CompanionEntity c, String event) {
        dev.aicompanion.CompanionDiagnostics.flush();
        var directory = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("logs").resolve("aicompanion");
        List<String> lines = new ArrayList<>();
        for (String name : List.of("companion-3.jsonl", "companion-2.jsonl", "companion-1.jsonl", "companion-latest.jsonl")) {
            try { lines.addAll(java.nio.file.Files.readAllLines(directory.resolve(name))); } catch (java.io.IOException | java.io.UncheckedIOException ignored) { }
        }
        return lines.stream().filter(line -> line.contains(c.getUUID().toString()) && line.contains("\"event\":\"" + event + "\"")).toList();
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (c.getItemBySlot(slot).is(item)) count += c.getItemBySlot(slot).getCount();
        return count;
    }
}

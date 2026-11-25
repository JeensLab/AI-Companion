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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The judge's own run of the owner's live scene: the blocks are HIS — placed by a real player, recorded by the game's own
 * bookkeeping (BlockEvent.EntityPlaceEvent, CompanionPlayerBuilds), floating with nothing under them, three to ten up and two to
 * eight across, plus the far corner of his numbers (25 across, 20 up). Each order is the player's phrase, each block must be found,
 * mined, the column taken down, the soil back in the bag and every other cell of the arena as it was. A gourd, a storage block,
 * an ore's item and a plain block, one per row, so a failure names the kind.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWayJudgeOwnBuildTests {
    private static final int SIDE = 48, TOP = 32, GROUND = 5, FEET = GROUND + 1;
    private static final int HOME_X = 20, HOME_Z = 16;

    /** His nearest gourd: a pumpkin he hung 5 up and 4 across his base, nothing under it — «добудь тыкву» takes it. */
    @GameTest(template = "way_wide", timeoutTicks = 16000)
    public static void hisPumpkinFiveUpIsFoundAndTaken(GameTestHelper h) {
        his(h, Blocks.PUMPKIN, Items.PUMPKIN, "minecraft:pumpkin", "добудь тыкву", 4, 2, 5, true);
    }

    /** The storage block he hung 7 up: «добудь блок лазурита» takes the block as it lies. */
    @GameTest(template = "way_wide", timeoutTicks = 16000)
    public static void hisLapisBlockSevenUpIsFoundAndTaken(GameTestHelper h) {
        his(h, Blocks.LAPIS_BLOCK, Items.LAPIS_BLOCK, "minecraft:lapis_block", "добудь блок лазурита", 6, 3, 7, true);
    }

    /**
     * An ore's item: gold ore he hung 3 up and 6 across, asked for as «добудь золото» — raw gold in the bag. The way's search
     * does not see an ore's drop (a bare hand takes nothing of an ore), so the mine's own scan is what must find the ore — and it
     * does not keep the player's site, so the ore he set down is dug like any other.
     */
    @GameTest(template = "way_wide", timeoutTicks = 16000)
    public static void hisGoldOreThreeUpGivesRawGold(GameTestHelper h) {
        his(h, Blocks.GOLD_ORE, Items.RAW_GOLD, "minecraft:raw_gold", "добудь золото", 6, 2, 3, false);
    }

    /** A plain block he hung 10 up and 8 across: «добудь булыжник» takes the block itself. */
    @GameTest(template = "way_wide", timeoutTicks = 16000)
    public static void hisCobblestoneTenUpIsFoundAndTaken(GameTestHelper h) {
        his(h, Blocks.COBBLESTONE, Items.COBBLESTONE, "minecraft:cobblestone", "добудь булыжник", 8, 2, 10, true);
    }

    /** His far corner: a barrel 25 across and 20 up, hung by him — found, mined, the column down, the soil back. */
    @GameTest(template = "way_wide", timeoutTicks = 40000)
    public static void hisBarrelTwentyFiveAcrossTwentyUpIsFoundAndTaken(GameTestHelper h) {
        his(h, Blocks.BARREL, Items.BARREL, "minecraft:barrel", "добудь бочку", 25, 0, 20, true);
    }

    /**
     * His whole scene at once: a chest and two planks he set down as his base, four kinds hung above it — the search says the
     * blocks were his own and taken, one line, and every one of the four ends in the bag.
     */
    @GameTest(template = "way_wide", timeoutTicks = 40000)
    public static void hisWholeSceneIsFoundAndTaken(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        base(h, c);
        List<Block> blocks = List.of(Blocks.PUMPKIN, Blocks.LAPIS_BLOCK, Blocks.COBBLESTONE, Blocks.GOLD_BLOCK);
        List<Item> items = List.of(Items.PUMPKIN, Items.LAPIS_BLOCK, Items.COBBLESTONE, Items.GOLD_BLOCK);
        List<String> ids = List.of("minecraft:pumpkin", "minecraft:lapis_block", "minecraft:cobblestone", "minecraft:gold_block");
        List<String> names = List.of("добудь тыкву", "добудь блок лазурита", "добудь булыжник", "добудь золотой блок");
        List<BlockPos> targets = List.of(new BlockPos(HOME_X + 3, FEET + 3, HOME_Z + 1), new BlockPos(HOME_X + 5, FEET + 6, HOME_Z + 3),
                new BlockPos(HOME_X + 7, FEET + 9, HOME_Z + 2), new BlockPos(HOME_X + 8, FEET + 10, HOME_Z + 4));
        for (int at = 0; at < blocks.size(); at++) placedByPlayer(h, c, targets.get(at), blocks.get(at));
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 32));
        int[] before = snapshot(h);
        int[] at = {0};
        List<CompletableFuture<CompanionPlanRunner.Outcome>> futures = new ArrayList<>(java.util.Collections.nCopies(ids.size(), (CompletableFuture<CompanionPlanRunner.Outcome>) null));
        h.onEachTick(() -> {
            if (at[0] >= ids.size()) return;
            if (futures.get(at[0]) == null) { futures.set(at[0], sayModel(h, c, names.get(at[0]), obtainPlan(ids.get(at[0])))); return; }
            if (!futures.get(at[0]).isDone()) return;
            String report = futures.get(at[0]).isCompletedExceptionally() ? "exception" : futures.get(at[0]).join().report();
            h.assertTrue(count(c, items.get(at[0])) >= 1, "«" + names.get(at[0]) + "» of his hung scene is not in the bag: " + report);
            h.assertTrue(!report.contains("Рядом нечего копать") && !report.contains("Рядом не вижу"), "The old refusal is back: " + report);
            for (int slot = 0; slot < c.getContainerSize(); slot++) {
                ItemStack stack = c.getItem(slot);
                if (!stack.is(Items.DIRT) && !stack.is(Items.DIAMOND_PICKAXE)) c.setItem(slot, ItemStack.EMPTY);
            }
            at[0]++;
        });
        h.succeedWhen(() -> {
            h.assertTrue(at[0] >= ids.size(), "Still on block " + at[0] + " of " + ids.size() + ": " + c.getLastTaskResult());
            for (BlockPos target : targets) h.assertBlockPresent(Blocks.AIR, target);
            assertRestored(h, before, targets.toArray(new BlockPos[0]));
            h.assertTrue(c.getTemporarySupportCount() == 0, "A column is still standing: " + c.getTemporarySupportCount());
            h.assertTrue(dirtAboveGround(h) == 0, "A support was left in the air: " + dirtAboveGround(h));
            h.assertTrue(count(c, Items.DIRT) + looseDirt(h) >= 32, "The soil of the columns was lost: "
                    + count(c, Items.DIRT) + " in the bag, " + looseDirt(h) + " loose");
            List<String> lines = journal(c, "way_search").stream().filter(entry -> entry.contains("\"chosen\":\"")).toList();
            h.assertTrue(lines.size() >= 4, "The search did not journal a chosen block for every order: " + lines);
        });
    }

    // ---- helpers ----

    /** A block he placed himself, floating {@code up} above his feet and {@code dx}, {@code dz} across his base: found and taken. */
    private static void his(GameTestHelper h, Block block, Item item, String id, String phrase, int dx, int dz, int up, boolean searchSees) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        base(h, c);
        BlockPos target = new BlockPos(HOME_X + dx, FEET + up, HOME_Z + dz);
        placedByPlayer(h, c, target, block);
        h.assertBlockPresent(Blocks.AIR, target.below());
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 24));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        if (searchSees) {
            var seen = c.obtainAvailable(id);
            h.assertTrue(seen.some(), "The search does not see «" + id + "» the player hung " + up + " up and " + dx + " across his own base: "
                    + seen + ", feet " + c.blockPosition() + ", block " + h.absolutePos(target));
        }
        var plan = c.obtainPlan(id, 1);
        h.assertTrue(!plan.refused(), "The plan refuses «" + id + "» he hung himself: " + plan.refusal());
        var future = sayModel(h, c, phrase, obtainPlan(id));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, item) >= 1, "«" + phrase + "» of the block he hung " + up + " up is not in the bag: " + report);
            h.assertTrue(!report.contains("Рядом нечего копать") && !report.contains("Рядом не вижу")
                    && !report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
            h.assertBlockPresent(Blocks.AIR, target);
            assertRestored(h, before, target);
            h.assertTrue(up < 4 || most[0] > 0, "No column was raised for " + up + " up: " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0, "The column is still standing: " + c.getTemporarySupportCount());
            h.assertTrue(count(c, Items.DIRT) + looseDirt(h) >= 24, "The soil of the column was lost: "
                    + count(c, Items.DIRT) + " in the bag, " + looseDirt(h) + " loose, " + report);
            h.assertTrue(dirtAboveGround(h) == 0, "A support was left in the air: " + dirtAboveGround(h));
            h.assertTrue(!h.getBlockState(new BlockPos(HOME_X + 2, FEET, HOME_Z + 2)).isAir(), "His own chest was taken: the site is not kept");
        });
    }

    /** His base: a chest and two planks, recorded by the game's own bookkeeping (a real place event of a real player). */
    private static void base(GameTestHelper h, CompanionEntity c) {
        placedByPlayer(h, c, new BlockPos(HOME_X + 2, FEET, HOME_Z + 2), Blocks.CHEST);
        placedByPlayer(h, c, new BlockPos(HOME_X + 1, FEET, HOME_Z + 2), Blocks.OAK_PLANKS);
        placedByPlayer(h, c, new BlockPos(HOME_X + 3, FEET, HOME_Z + 2), Blocks.OAK_PLANKS);
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
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-way-judge-own-build", () -> false));
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

    private static int[] snapshot(GameTestHelper h) {
        int[] cells = new int[SIDE * TOP * SIDE];
        int at = 0;
        for (int y = 0; y < TOP; y++) for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++)
            cells[at++] = Block.getId(h.getBlockState(new BlockPos(x, y, z)));
        return cells;
    }

    private static void assertRestored(GameTestHelper h, int[] before, BlockPos... mined) {
        Set<BlockPos> allowed = new HashSet<>(List.of(mined));
        int at = 0;
        for (int y = 0; y < TOP; y++) for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            int now = Block.getId(h.getBlockState(pos));
            int was = before[at++];
            if (now == was) continue;
            h.assertTrue(allowed.contains(pos) && h.getBlockState(pos).isAir(),
                    "A cell changed at " + pos + ": " + BuiltInRegistries.BLOCK.getKey(h.getBlockState(pos).getBlock()) + ", allowed " + allowed);
        }
    }

    private static int dirtAboveGround(GameTestHelper h) {
        int count = 0;
        for (int x = 1; x < SIDE - 1; x++) for (int z = 1; z < SIDE - 1; z++) for (int y = GROUND + 1; y < TOP - 1; y++)
            if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT)) count++;
        return count;
    }

    private static int looseDirt(GameTestHelper h) {
        int count = 0;
        var box = new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(1, 1, 1)).getCenter(),
                h.absolutePos(new BlockPos(SIDE - 2, TOP - 2, SIDE - 2)).getCenter());
        for (net.minecraft.world.entity.item.ItemEntity entity : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box))
            if (entity.getItem().is(Items.DIRT)) count += entity.getItem().getCount();
        return count;
    }

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

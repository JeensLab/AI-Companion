package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionObtain;
import dev.aicompanion.entity.CompanionObtain.Step;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Olegs errand into the Nether (15.11): quartz, netherrack and basalt are not in the overworld, so the plan goes through a lit portal
 * he can walk to (nether_go), digs there at the exit portal and comes back by it (nether_back), and the rest of the chain is made at home.
 * The model never writes these two steps; they only come out of obtain_item.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionNetherErrandTests {
    private static final int GROUND = 5;
    private static final String NO_PORTAL = "Рядом нет портала в Незер — построй его или скажи «построй портал».";
    /** The chunks of the Nether arena a test forced: let go after the batch, whatever the test did. The tests are one batch each: eight overworld blocks are one in the Nether, so arenas of tests side by side overlap. */
    static final List<ChunkPos> FORCED = new ArrayList<>();

    /**
     * Whether the Nether has handed out the entities of the forced chunks. Until it has, the server thread yields a few
     * milliseconds a tick: a test server ticks as fast as it can, while those entities come from the disk off its thread, and
     * under load (CI shards on two cores, 19.11) a test's whole tick budget went by before they did («Ticked 0 times only»).
     */
    static boolean forcedLoaded(ServerLevel nether) {
        if (FORCED.stream().allMatch(chunk -> nether.areEntitiesLoaded(chunk.toLong()))) return true;
        try { Thread.sleep(5); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        return false;
    }

    @AfterBatch(batch = "errand0")
    public static void afterErrand0(ServerLevel level) { afterErrand(level); }

    @AfterBatch(batch = "errand1")
    public static void afterErrand1(ServerLevel level) { afterErrand(level); }

    @AfterBatch(batch = "errand2")
    public static void afterErrand2(ServerLevel level) { afterErrand(level); }

    @AfterBatch(batch = "errand3")
    public static void afterErrand3(ServerLevel level) { afterErrand(level); }

    @AfterBatch(batch = "errand4")
    public static void afterErrand4(ServerLevel level) { afterErrand(level); }

    @AfterBatch(batch = "errand5")
    public static void afterErrand5(ServerLevel level) { afterErrand(level); }

    @AfterBatch(batch = "errand6")
    public static void afterErrand6(ServerLevel level) { afterErrand(level); }

    @AfterBatch(batch = "errand7")
    public static void afterErrand7(ServerLevel level) { afterErrand(level); }

    @AfterBatch(batch = "errand8")
    public static void afterErrand8(ServerLevel level) { afterErrand(level); }

    @AfterBatch(batch = "errand9")
    public static void afterErrand9(ServerLevel level) { afterErrand(level); }

    @AfterBatch(batch = "errand10")
    public static void afterErrand10(ServerLevel level) { afterErrand(level); }

    @AfterBatch(batch = "errand11")
    public static void afterErrand11(ServerLevel level) { afterErrand(level); }

    static void afterErrand(ServerLevel level) {
        CompanionFixtureCleanup.afterAtomicTests(level);
        var nether = level.getServer().getLevel(Level.NETHER);
        if (nether != null) for (ChunkPos chunk : FORCED) nether.setChunkForced(chunk.x, chunk.z, false);
        FORCED.clear();
    }

    private static List<Step> steps(CompanionObtain.Plan plan) { return plan.steps(); }

    private static CompanionObtain.Plan plan(ServerLevel level, boolean bench, List<ItemStack> carried, String item, int count, CompanionObtain.Trip trip) {
        return CompanionObtain.plan(level, carried, bench, item, count, null, null, trip);
    }

    /** Overworld, a lit portal near: the pickaxe is made at home first, then go, dig at the exit, back. Quartz is a mine_ore, the stone a gather_block. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void quartzNetherrackAndBasaltAreFetchedThroughThePortal(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos portal = h.absolutePos(new BlockPos(3, 2, 3));
        var trip = new CompanionObtain.Trip(portal, false);
        var go = new Step("nether_go", portal.toShortString(), 1);
        var back = new Step("nether_back", "", 1);
        var pick = List.of(new ItemStack(Items.WOODEN_PICKAXE));
        var quartz = plan(level, false, pick, "minecraft:quartz", 3, trip);
        h.assertTrue(steps(quartz).equals(List.of(go, new Step("mine_ore", "minecraft:quartz_ore", 3), back)), "Quartz: " + quartz);
        for (String stone : List.of("minecraft:netherrack", "minecraft:basalt")) {
            var gather = plan(level, false, pick, stone, 8, trip);
            h.assertTrue(steps(gather).equals(List.of(go, new Step("gather_block", stone, 8), back)), stone + ": " + gather);
        }
        // No pickaxe: it is made at home, before the walk to the portal, never in the Nether.
        var bare = plan(level, false, List.of(), "minecraft:quartz", 3, trip);
        int made = -1, walk = -1;
        for (int i = 0; i < bare.steps().size(); i++) {
            if (bare.steps().get(i).type().equals("craft_item") && bare.steps().get(i).item().equals("minecraft:wooden_pickaxe")) made = i;
            if (bare.steps().get(i).type().equals("nether_go")) walk = i;
        }
        h.assertTrue(!bare.refused() && made >= 0 && made < walk, "The pickaxe is not made before the walk: " + bare);
        h.assertTrue(bare.steps().getLast().equals(back), "No way back: " + bare);
        h.succeed();
    }

    /** The comparator: what can be made at home is made before he goes, the craft that needs the quartz comes after nether_back. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aComparatorIsCraftedAfterTheWayBack(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos portal = h.absolutePos(new BlockPos(3, 2, 3));
        var trip = new CompanionObtain.Trip(portal, false);
        var go = new Step("nether_go", portal.toShortString(), 1);
        var back = new Step("nether_back", "", 1);
        var ready = List.of(new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.REDSTONE_TORCH, 3), new ItemStack(Items.STONE, 3));
        var plan = plan(level, true, ready, "minecraft:comparator", 1, trip);
        h.assertTrue(steps(plan).equals(List.of(go, new Step("mine_ore", "minecraft:quartz_ore", 1), back, new Step("craft_item", "minecraft:comparator", 1))), "Comparator: " + plan);
        // The torches are still to make: that is the preparation at home, before the walk.
        var torches = List.of(new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.REDSTONE, 3), new ItemStack(Items.STICK, 3), new ItemStack(Items.STONE, 3));
        var prepared = plan(level, true, torches, "minecraft:comparator", 1, trip);
        List<String> types = prepared.steps().stream().map(step -> step.type() + ":" + step.item()).toList();
        int torch = types.indexOf("craft_item:minecraft:redstone_torch");
        h.assertTrue(!prepared.refused() && torch >= 0 && torch < types.indexOf("nether_go:" + portal.toShortString()), "The torches are not made before the walk: " + types);
        h.assertTrue(types.indexOf("nether_go:" + portal.toShortString()) < types.indexOf("mine_ore:minecraft:quartz_ore")
                && types.indexOf("mine_ore:minecraft:quartz_ore") < types.indexOf("nether_back:")
                && types.indexOf("nether_back:") < types.indexOf("craft_item:minecraft:comparator"), "Order: " + types);
        h.succeed();
    }

    /** No lit portal within reach: said before any step, and no step is planned for it. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void withNoPortalTheErrandIsRefused(GameTestHelper h) {
        var level = h.getLevel();
        var pick = List.of(new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.REDSTONE_TORCH, 3), new ItemStack(Items.STONE, 3));
        for (String item : List.of("minecraft:quartz", "minecraft:netherrack", "minecraft:basalt", "minecraft:comparator")) {
            for (var trip : new CompanionObtain.Trip[] {null, new CompanionObtain.Trip(null, false)}) {
                var plan = plan(level, true, pick, item, 3, trip);
                h.assertTrue(plan.refused() && plan.steps().isEmpty() && NO_PORTAL.equals(plan.refusal()), item + ": " + plan);
            }
        }
        h.succeed();
    }

    /**
     * Netherrack that lies about him in the overworld is not a source (it is almost always the player's: a portal's surround, a fireplace):
     * it goes through the portal like quartz, whatever lies near; enough in the bag is no trip.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void whatIsAtHandNeedsNoTrip(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos portal = h.absolutePos(new BlockPos(3, 2, 3));
        var trip = new CompanionObtain.Trip(portal, false);
        var pick = List.of(new ItemStack(Items.WOODEN_PICKAXE));
        for (String stone : List.of("minecraft:netherrack", "minecraft:basalt")) {
            var near = CompanionObtain.plan(level, pick, false, stone, 8, thing -> true, null, trip);
            h.assertTrue(steps(near).equals(List.of(new Step("nether_go", portal.toShortString(), 1), new Step("gather_block", stone, 8), new Step("nether_back", "", 1))), stone + " at hand: " + near);
            var nowhere = CompanionObtain.plan(level, pick, false, stone, 8, thing -> true, null, null);
            h.assertTrue(nowhere.refused() && NO_PORTAL.equals(nowhere.refusal()), stone + " with no portal: " + nowhere);
        }
        var have = plan(level, false, List.of(new ItemStack(Items.QUARTZ, 5)), "minecraft:quartz", 3, trip);
        h.assertTrue(!have.refused() && have.steps().isEmpty(), "Quartz in the bag: " + have);
        h.succeed();
    }

    /** In the Nether on the errand: the digging, then the way back, then the rest at home; with nothing left to dig the way back comes first. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void inTheNetherTheWayBackFollowsTheDigging(GameTestHelper h) {
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        var away = new CompanionObtain.Trip(null, true);
        var back = new Step("nether_back", "", 1);
        var pick = List.of(new ItemStack(Items.WOODEN_PICKAXE));
        var quartz = plan(nether, false, pick, "minecraft:quartz", 3, away);
        h.assertTrue(steps(quartz).equals(List.of(new Step("mine_ore", "minecraft:quartz_ore", 3), back)), "Quartz in the Nether: " + quartz);
        var stone = plan(nether, false, pick, "minecraft:basalt", 4, away);
        h.assertTrue(steps(stone).equals(List.of(new Step("gather_block", "minecraft:basalt", 4), back)), "Basalt in the Nether: " + stone);
        var ready = List.of(new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.REDSTONE_TORCH, 3), new ItemStack(Items.STONE, 3));
        var comparator = plan(nether, true, ready, "minecraft:comparator", 1, away);
        h.assertTrue(steps(comparator).equals(List.of(new Step("mine_ore", "minecraft:quartz_ore", 1), back, new Step("craft_item", "minecraft:comparator", 1))), "Comparator in the Nether: " + comparator);
        var dug = plan(nether, false, List.of(new ItemStack(Items.QUARTZ, 3)), "minecraft:quartz", 3, away);
        h.assertTrue(steps(dug).equals(List.of(back)), "Nothing left to dig: " + dug);
        var crafted = plan(nether, true, List.of(new ItemStack(Items.QUARTZ, 1), new ItemStack(Items.REDSTONE_TORCH, 3), new ItemStack(Items.STONE, 3)), "minecraft:comparator", 1, away);
        h.assertTrue(steps(crafted).equals(List.of(back, new Step("craft_item", "minecraft:comparator", 1))), "Everything dug, the craft is at home: " + crafted);
        // The player is in the Nether with him and no errand is on: it is any gathering there, with no way back planned.
        var together = plan(nether, false, pick, "minecraft:quartz", 3, new CompanionObtain.Trip(null, false));
        h.assertTrue(steps(together).equals(List.of(new Step("mine_ore", "minecraft:quartz_ore", 3))), "With the player: " + together);
        h.succeed();
    }

    /** Chunk loading off (his own switch or the player's): the Nether's chunks would unload around him, so no trip is planned. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void withChunkLoadingOffTheTripIsNotPlanned(GameTestHelper h) {
        var level = h.getLevel();
        var trip = new CompanionObtain.Trip(h.absolutePos(new BlockPos(3, 2, 3)), false, false);
        var ready = List.of(new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.REDSTONE_TORCH, 3), new ItemStack(Items.STONE, 3));
        for (String item : List.of("minecraft:quartz", "minecraft:netherrack", "minecraft:basalt", "minecraft:comparator")) {
            var plan = plan(level, true, ready, item, 3, trip);
            h.assertTrue(plan.refused() && plan.steps().isEmpty() && plan.refusal().contains("подгрузка чанков") && plan.refusal().contains("Работа вдали от игрока"), item + ": " + plan);
        }
        h.succeed();
    }

    /** The same with a real companion: both switches on plan the trip; either one off refuses it, and nether_go started anyway refuses too. */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void neitherSwitchOffLetsHimGoToTheNetherAlone(GameTestHelper h) {
        ground(h);
        lightPortal(h);
        CompanionEntity companion = spawn(h, 6, 16);
        companion.setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        var on = companion.obtainPlan("minecraft:quartz", 3);
        h.assertTrue(!on.refused() && on.steps().stream().anyMatch(step -> step.type().equals("nether_go")), "Both switches on: " + on);
        for (int off = 0; off < 2; off++) {
            companion.setLifestyle(off != 0, true);
            owner.getPersistentData().putBoolean("aicompanion:chunk_loading_enabled", off == 0);
            var plan = companion.obtainPlan("minecraft:quartz", 3);
            h.assertTrue(plan.refused() && plan.steps().isEmpty() && plan.refusal().contains("подгрузка чанков"), (off == 0 ? "His own" : "The player's") + " switch off: " + plan);
            var go = companion.startWork("nether_go", "", 1);
            h.assertTrue(!go.success() && go.message().contains("подгрузка чанков") && !companion.hasActiveWorkOrder(), "nether_go went on: " + go.message());
        }
        h.succeed();
    }

    /** The model cannot write the errand's steps: they are no action of the plan codec. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void theModelCannotWriteTheErrandSteps(GameTestHelper h) {
        h.assertTrue(!CompanionPlanCodec.ACTIONS.contains("nether_go") && !CompanionPlanCodec.ACTIONS.contains("nether_back"), "The codec allows an errand step");
        for (String step : List.of("nether_go", "nether_back")) {
            boolean refused = false;
            try { CompanionPlanCodec.decode(JsonParser.parseString("{\"content\":null,\"tool_calls\":[{\"function\":{\"name\":\"queue_companion_plan\",\"arguments\":\"{\\\"steps\\\":[{\\\"action\\\":\\\"" + step + "\\\"}]}\"}}]}").getAsJsonObject()); }
            catch (IllegalStateException expected) { refused = true; }
            h.assertTrue(refused, step + " was let through");
        }
        h.succeed();
    }

    /**
     * A save that has the errand and the in-transit flag, loaded when no crossing marked it (a world loaded, not a portal taken), is a
     * failure «прервалась выходом из мира» and the errand stays recorded. A save of the old kind (no errand) says what it always did.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aTransitFlagFoundOnLoadIsAFailureAndTheErrandStays(GameTestHelper h) {
        CompanionEntity saved = h.spawn(AICompanionMod.COMPANION.get(), 2, 2, 2);
        CompoundTag tag = new CompoundTag();
        saved.addAdditionalSaveData(tag);
        tag.putBoolean("WorkActive", true);
        tag.putLong("WorkSequence", 5);
        CompoundTag old = tag.copy();
        CompoundTag errand = new CompoundTag();
        errand.putLong("Home", h.absolutePos(new BlockPos(3, 2, 3)).asLong());
        errand.putLong("Since", 100);
        errand.putLong("Deadline", 6100);
        tag.put("Errand", errand);
        CompoundTag transit = new CompoundTag();
        transit.putLong("Token", 987654321L);
        transit.putLong("Work", 5);
        transit.putBoolean("ToNether", true);
        tag.put("ErrandTransit", transit);

        CompanionEntity reloaded = h.spawn(AICompanionMod.COMPANION.get(), 4, 2, 4);
        reloaded.readAdditionalSaveData(tag);
        h.assertTrue(reloaded.getLastTaskResult().contains("прервалась выходом из мира"), "No interruption said: " + reloaded.getLastTaskResult());
        var result = reloaded.workResult(5);
        h.assertTrue(result != null && !result.success() && result.message().contains("прервалась выходом из мира"), "The flag left on load is no world-load failure: " + result);
        CompoundTag again = new CompoundTag();
        reloaded.addAdditionalSaveData(again);
        h.assertTrue(again.contains("Errand") && again.getCompound("Errand").getLong("Home") == errand.getLong("Home"), "The errand was lost: " + again.get("Errand"));
        h.assertTrue(!again.contains("ErrandTransit"), "The flag was kept");

        CompanionEntity plain = h.spawn(AICompanionMod.COMPANION.get(), 6, 2, 6);
        plain.readAdditionalSaveData(old);
        h.assertTrue(plain.getLastTaskResult().contains("прервалась выходом из мира"), "The old save says another thing: " + plain.getLastTaskResult());
        CompoundTag none = new CompoundTag();
        plain.addAdditionalSaveData(none);
        h.assertTrue(!none.contains("Errand"), "An errand out of nothing: " + none.get("Errand"));
        h.succeed();
    }

    /**
     * «Добудь 3 кварца» with a lit portal in the overworld: he walks in, is in the Nether with the errand recorded (the exit portal in it),
     * digs three quartz there, goes back by the exit portal, and stands in the overworld with three, following, the errand lifted.
     * The Nether is a shut arena above its roof with a portal of its own at the place the game sends him to, and ores on both sides of it.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 6000, batch = "errand0")
    public static void quartzIsFetchedThroughThePortalAndBack(GameTestHelper h) {
        ground(h);
        lightPortal(h);
        var server = h.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        BlockPos at = h.absolutePos(new BlockPos(15, GROUND + 2, 16));
        BlockPos[] ores = arena(nether, Math.floorDiv(at.getX(), 8), Math.floorDiv(at.getZ(), 8));
        CompanionEntity companion = spawn(h, 6, 16);
        companion.setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        UUID id = companion.getUUID();
        boolean[] exitRecorded = {false};
        // The entities of the arena's chunks load a little after the chunks: he is sent in only when they have, as in a world where the player is near.
        List<CompletableFuture<CompanionPlanRunner.Outcome>> started = new ArrayList<>();
        h.onEachTick(() -> {
            if (started.isEmpty() && forcedLoaded(nether))
                started.add(run(h, companion, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:quartz\",\"count\":3}]"));
            if (nether.getEntity(id) instanceof CompanionEntity there) {
                CompoundTag tag = new CompoundTag();
                there.addAdditionalSaveData(tag);
                if (tag.contains("Errand") && tag.getCompound("Errand").contains("Exit")) exitRecorded[0] = true;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(!started.isEmpty(), "The arena's entities are not loaded yet");
            var future = started.getFirst();
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + describe(h, nether, id));
            h.assertTrue(exitRecorded[0], "He was never seen in the Nether with the errand and its exit portal recorded");
            h.assertTrue(h.getLevel().getEntity(id) instanceof CompanionEntity, "Not back in the overworld: " + describe(h, nether, id) + " | " + future.join().report());
            CompanionEntity home = (CompanionEntity) h.getLevel().getEntity(id);
            h.assertTrue(nether.getEntity(id) == null, "He is in two worlds");
            String report = future.join().report();
            h.assertTrue(count(home, Items.QUARTZ) == 3, "Quartz " + count(home, Items.QUARTZ) + ": " + report);
            h.assertTrue(report.contains("Незер") && !report.contains("остановлен"), "The report: " + report);
            CompoundTag tag = new CompoundTag();
            home.addAdditionalSaveData(tag);
            h.assertTrue(!tag.contains("Errand"), "The errand is still on: " + tag.get("Errand"));
            h.assertTrue(home.isFollowing(), "He does not follow the player after the errand");
            int left = 0;
            for (BlockPos ore : ores) if (nether.getBlockState(ore).is(Blocks.NETHER_QUARTZ_ORE)) left++;
            h.assertTrue(left == ores.length - 3, "Ores left " + left + " of " + ores.length);
        });
    }

    /** Whole errand length, as the game records it at the arrival. */
    private static final long MOST = 20 * 60 * 5;
    private static final String QUARTZ_6 = "[{\"action\":\"obtain_item\",\"item\":\"minecraft:quartz\",\"count\":6}]";

    /**
     * Health below half in the Nether, in the middle of the digging: the digging is cut short with an honest count of what was gained,
     * the plan stops with it, and he walks out by the exit portal; nothing he broke is lost.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 6000, batch = "errand1")
    public static void lowHealthInTheNetherCutsTheDiggingShortAndHeGoesBack(GameTestHelper h) {
        ground(h);
        lightPortal(h);
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = arenaCell(h);
        BlockPos[] ores = arena(nether, cell[0], cell[1]);
        CompanionEntity companion = spawn(h, 6, 16);
        companion.setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        UUID id = companion.getUUID();
        List<CompletableFuture<CompanionPlanRunner.Outcome>> started = new ArrayList<>();
        boolean[] hurt = {false};
        long[] dig = {0};
        h.onEachTick(() -> {
            if (started.isEmpty() && forcedLoaded(nether)) started.add(run(h, companion, QUARTZ_6));
            if (!hurt[0] && nether.getEntity(id) instanceof CompanionEntity there && count(there, Items.QUARTZ) >= 1) { dig[0] = there.activeWorkId(); there.setHealth(8); hurt[0] = true; }
        });
        h.succeedWhen(() -> {
            h.assertTrue(hurt[0], "He has not dug a quartz yet");
            h.assertTrue(started.getFirst().isDone(), "Plan still running: " + describe(h, nether, id));
            h.assertTrue(h.getLevel().getEntity(id) instanceof CompanionEntity, "Not back in the overworld: " + describe(h, nether, id));
            CompanionEntity home = (CompanionEntity) h.getLevel().getEntity(id);
            String report = started.getFirst().join().report();
            h.assertTrue(report.contains("Опасно, возвращаюсь с тем, что есть"), "The report: " + report);
            // What the plan polls (workResult by the dig's id), not only what he says.
            var polled = dig[0] == 0 ? null : home.workResult(dig[0]);
            h.assertTrue(polled != null && polled.message().contains("Опасно, возвращаюсь с тем, что есть"), "The dig's own result: " + polled);
            var said = java.util.regex.Pattern.compile("прибавилось (\\d+) «").matcher(report);
            h.assertTrue(said.find(), "No count in the report: " + report);
            int told = Integer.parseInt(said.group(1)), have = count(home, Items.QUARTZ);
            h.assertTrue(told >= 1 && told <= have && have < 6 && have == broken(nether, ores), "Said " + told + ", bag " + have + ", ores broken " + broken(nether, ores) + ": " + report);
            CompoundTag tag = new CompoundTag();
            home.addAdditionalSaveData(tag);
            h.assertTrue(!tag.contains("Errand") && home.isFollowing(), "The errand is not over: " + tag.get("Errand"));
        });
    }

    /** «Стой» while he digs in the Nether (the plan is stopped, no work left): he walks to the exit portal by himself and is home. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000, batch = "errand2")
    public static void aStoppedPlanInTheNetherSendsHimBackByHimself(GameTestHelper h) {
        ground(h);
        lightPortal(h);
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = arenaCell(h);
        arena(nether, cell[0], cell[1]);
        CompanionEntity companion = spawn(h, 6, 16);
        companion.setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        UUID id = companion.getUUID();
        List<CompletableFuture<CompanionPlanRunner.Outcome>> started = new ArrayList<>();
        boolean[] stopped = {false};
        h.onEachTick(() -> {
            if (started.isEmpty() && forcedLoaded(nether)) started.add(run(h, companion, QUARTZ_6));
            if (!stopped[0] && nether.getEntity(id) instanceof CompanionEntity there && count(there, Items.QUARTZ) >= 1) { there.stopGatheringForPlayer(); stopped[0] = true; }
        });
        h.succeedWhen(() -> {
            h.assertTrue(stopped[0], "He has not dug a quartz yet");
            h.assertTrue(h.getLevel().getEntity(id) instanceof CompanionEntity, "Still in the Nether: " + describe(h, nether, id));
            CompanionEntity home = (CompanionEntity) h.getLevel().getEntity(id);
            CompoundTag tag = new CompoundTag();
            home.addAdditionalSaveData(tag);
            h.assertTrue(!tag.contains("Errand") && home.isFollowing() && count(home, Items.QUARTZ) >= 1, "Not home with what he dug: " + tag.get("Errand"));
        });
    }

    /** The world was loaded with him in the Nether (an errand in his data, no work, no exit portal in it): he walks out by the nearest portal. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000, batch = "errand3")
    public static void anErrandFoundOnLoadWithNoExitSendsHimBackByTheNearestPortal(GameTestHelper h) {
        walksOutOfTheNether(h, null);
    }

    /** The exit portal in his record is gone (a stale one): the same, by the nearest lit portal. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000, batch = "errand4")
    public static void anErrandWithAGoneExitPortalSendsHimBackByTheNearestPortal(GameTestHelper h) {
        int[] cell = arenaCell(h);
        walksOutOfTheNether(h, new BlockPos(cell[0] + 5, 151, cell[1] + 40).asLong());
    }

    private static void walksOutOfTheNether(GameTestHelper h, Long exit) {
        ground(h);
        lightPortal(h);
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = arenaCell(h);
        arena(nether, cell[0], cell[1]);
        CompanionEntity there = restored(h, nether, cell, exit, MOST);
        UUID id = there.getUUID();
        boolean[] added = {false};
        h.onEachTick(() -> {
            if (!added[0] && forcedLoaded(nether)) { nether.addFreshEntity(there); added[0] = true; }
        });
        h.succeedWhen(() -> {
            h.assertTrue(added[0] && h.getLevel().getEntity(id) instanceof CompanionEntity, "Still in the Nether: " + describe(h, nether, id));
            CompanionEntity home = (CompanionEntity) h.getLevel().getEntity(id);
            CompoundTag tag = new CompoundTag();
            home.addAdditionalSaveData(tag);
            h.assertTrue(!tag.contains("Errand") && home.isFollowing() && nether.getEntity(id) == null, "The errand is not over: " + tag.get("Errand"));
        });
    }

    /** The exit portal in his record is 70 blocks from the quartz: it is not taken, whatever lies in his search radius; then he goes back. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000, batch = "errand5")
    public static void nothingFartherThanFortyEightBlocksFromTheExitPortalIsTaken(GameTestHelper h) {
        ground(h);
        lightPortal(h);
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = arenaCell(h);
        BlockPos[] ores = arena(nether, cell[0], cell[1]);
        CompanionEntity there = restored(h, nether, cell, new BlockPos(cell[0] + 70, 151, cell[1]).asLong(), MOST);
        UUID id = there.getUUID();
        List<CompletableFuture<CompanionPlanRunner.Outcome>> started = new ArrayList<>();
        h.onEachTick(() -> {
            if (started.isEmpty() && forcedLoaded(nether)) {
                nether.addFreshEntity(there);
                started.add(run(h, there, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:quartz\",\"count\":3}]"));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(!started.isEmpty() && started.getFirst().isDone(), "Plan still running: " + describe(h, nether, id));
            h.assertTrue(h.getLevel().getEntity(id) instanceof CompanionEntity, "Still in the Nether: " + describe(h, nether, id));
            CompanionEntity home = (CompanionEntity) h.getLevel().getEntity(id);
            h.assertTrue(broken(nether, ores) == 0 && count(home, Items.QUARTZ) == 0, "He took ore beyond the radius: broken " + broken(nether, ores) + ", bag " + count(home, Items.QUARTZ) + ": " + started.getFirst().join().report());
        });
    }

    /** Five minutes are up (here: three seconds): the digging stops with «Время вышло» and an honest count, and he is back. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000, batch = "errand6")
    public static void whenTheTimeIsUpHeLeavesTheDiggingAndGoesBack(GameTestHelper h) {
        ground(h);
        lightPortal(h);
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = arenaCell(h);
        BlockPos[] ores = arena(nether, cell[0], cell[1]);
        CompanionEntity there = restored(h, nether, cell, new BlockPos(cell[0], 151, cell[1]).asLong(), 60);
        UUID id = there.getUUID();
        List<CompletableFuture<CompanionPlanRunner.Outcome>> started = new ArrayList<>();
        long[] dig = {0};
        h.onEachTick(() -> {
            if (started.isEmpty() && forcedLoaded(nether)) {
                nether.addFreshEntity(there);
                started.add(run(h, there, QUARTZ_6));
            }
            if (dig[0] == 0 && !started.isEmpty()) dig[0] = there.activeWorkId();
        });
        h.succeedWhen(() -> {
            h.assertTrue(!started.isEmpty() && started.getFirst().isDone(), "Plan still running: " + describe(h, nether, id));
            h.assertTrue(h.getLevel().getEntity(id) instanceof CompanionEntity, "Still in the Nether: " + describe(h, nether, id));
            CompanionEntity home = (CompanionEntity) h.getLevel().getEntity(id);
            String report = started.getFirst().join().report();
            var said = java.util.regex.Pattern.compile("прибавилось (\\d+) «").matcher(report);
            h.assertTrue(report.contains("Время вышло, возвращаюсь с тем, что есть") && said.find(), "The report: " + report);
            var polled = dig[0] == 0 ? null : home.workResult(dig[0]);
            h.assertTrue(polled != null && polled.message().contains("Время вышло, возвращаюсь с тем, что есть"), "The dig's own result: " + polled);
            int told = Integer.parseInt(said.group(1)), have = count(home, Items.QUARTZ);
            h.assertTrue(told <= have && have == broken(nether, ores) && have < ores.length, "Said " + told + ", bag " + have + ", ores broken " + broken(nether, ores));
        });
    }

    /** With no portal at all to go out by he stays, and the Nether with only him in it goes on ticking past the empty-world limit (300 ticks). */
    @GameTest(template = "tall_tree", timeoutTicks = 1000, batch = "errand7")
    public static void aloneInTheNetherWithNoPortalHeStaysAndKeepsTicking(GameTestHelper h) {
        ground(h);
        lightPortal(h);
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = arenaCell(h);
        arena(nether, cell[0], cell[1]);
        for (int x = cell[0]; x <= cell[0] + 1; x++) for (int y = 151; y <= 153; y++) nether.setBlockAndUpdate(new BlockPos(x, y, cell[1]), Blocks.AIR.defaultBlockState());
        CompanionEntity there = restored(h, nether, cell, null, MOST);
        UUID id = there.getUUID();
        boolean[] added = {false};
        h.onEachTick(() -> {
            if (!added[0] && forcedLoaded(nether)) { nether.addFreshEntity(there); added[0] = true; }
        });
        h.succeedWhen(() -> {
            h.assertTrue(added[0] && there.tickCount >= 450, "Ticked " + there.tickCount + " times only");
            h.assertTrue(nether.getEntity(id) == there && h.getLevel().getEntity(id) == null, "He left the Nether without a portal");
            CompoundTag tag = new CompoundTag();
            there.addAdditionalSaveData(tag);
            h.assertTrue(tag.contains("Errand"), "The errand was dropped");
        });
    }

    /**
     * «Стой» while he digs in the Nether on the errand: the dig is not put aside (a dig of another world is dropped anyway) but ended, the
     * order is answered by what he does (comes back), not by «not loaded near you», and he walks out.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 6000, batch = "errand11")
    public static void aStopInTheNetherEndsTheDigAndIsAnsweredHonestly(GameTestHelper h) {
        ground(h);
        lightPortal(h);
        var server = h.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = arenaCell(h);
        BlockPos[] ores = arena(nether, cell[0], cell[1]);
        CompanionEntity there = restored(h, nether, cell, new BlockPos(cell[0], 151, cell[1]).asLong(), MOST);
        var owner = server.getPlayerList().getPlayer(there.getOwnerId());
        UUID id = there.getUUID();
        boolean[] stopped = {false};
        long[] dig = {0};
        h.onEachTick(() -> {
            if (stopped[0] || !forcedLoaded(nether)) return;
            if (dig[0] == 0) {
                nether.addFreshEntity(there);
                var started = there.startWork("mine_ore", "minecraft:quartz_ore", 6);
                h.assertTrue(started.success() && there.activeWorkId() != 0, "No dig: " + started.message());
                dig[0] = there.activeWorkId();
                return;
            }
            if (broken(nether, ores) < 1) return;
            h.assertTrue(!there.parkWork() && there.activeWorkId() == dig[0], "The dig was put aside in the Nether");
            var said = CompanionEvents.executeAction(owner, "wait", "", 1);
            h.assertTrue(said.success() && !said.message().contains("не загружен") && said.message().contains("Незер"), "The stop's answer: " + said.message());
            var polled = there.workResult(dig[0]);
            h.assertTrue(there.activeWorkId() == 0 && polled != null && !polled.message().contains("Отложил"), "The dig was not ended by the stop: " + polled);
            stopped[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(stopped[0] && h.getLevel().getEntity(id) instanceof CompanionEntity, "Still in the Nether: " + describe(h, nether, id));
            h.assertTrue(nether.getEntity(id) == null, "He is in two worlds");
        });
    }

    /** The player is in the Nether but far from him (past 32 blocks): the errand and its watch go on; near him, it is lifted. */
    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "errand8")
    public static void onlyAPlayerNearHimInTheNetherEndsTheErrand(GameTestHelper h) {
        ground(h);
        lightPortal(h);
        var server = h.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = arenaCell(h);
        arena(nether, cell[0], cell[1]);
        CompanionEntity there = restored(h, nether, cell, new BlockPos(cell[0], 151, cell[1]).asLong(), MOST);
        var owner = server.getPlayerList().getPlayer(there.getOwnerId());
        owner.setInvulnerable(true);
        BlockPos back = h.absolutePos(new BlockPos(2, GROUND + 1, 2));
        UUID id = there.getUUID();
        long[] since = {-1};
        h.onEachTick(() -> {
            if (since[0] < 0) {
                if (!forcedLoaded(nether)) return;
                nether.addFreshEntity(there);
                owner.teleportTo(nether, cell[0] + 60.5, 160, cell[1] + 0.5, 0, 0);
                since[0] = h.getTick();
                return;
            }
            long passed = h.getTick() - since[0];
            CompoundTag tag = new CompoundTag();
            there.addAdditionalSaveData(tag);
            if (passed == 25) {
                h.assertTrue(tag.contains("Errand") && owner.level() == nether, "A player far off in the Nether ended the errand: " + tag.get("Errand"));
                owner.teleportTo(nether, cell[0] + 1.5, 150, cell[1] + 5.5, 0, 0);
            }
            if (passed > 25 && !tag.contains("Errand")) {
                h.assertTrue(nether.getEntity(id) == there && there.isFollowing(), "Not lifted by the player's coming: " + describe(h, nether, id));
                owner.teleportTo(h.getLevel(), back.getX() + .5, back.getY(), back.getZ() + .5, 0, 0);
                h.succeed();
            }
        });
    }

    /** In the Nether with no errand record at all: nether_back is no «already home»; he walks out by the nearest lit portal. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000, batch = "errand9")
    public static void netherBackWithNoRecordWalksOutByTheNearestPortal(GameTestHelper h) {
        ground(h);
        lightPortal(h);
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = arenaCell(h);
        arena(nether, cell[0], cell[1]);
        CompanionEntity there = restored(h, nether, cell, null, -1);
        UUID id = there.getUUID();
        boolean[] added = {false};
        h.onEachTick(() -> {
            if (added[0] || !forcedLoaded(nether)) return;
            nether.addFreshEntity(there);
            var back = there.startWork("nether_back", "", 1);
            h.assertTrue(back.success() && !back.message().contains("уже в верхнем") && there.hasActiveWorkOrder(), "No walk out: " + back.message());
            added[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(added[0] && h.getLevel().getEntity(id) instanceof CompanionEntity, "Still in the Nether: " + describe(h, nether, id));
            h.assertTrue(nether.getEntity(id) == null, "He is in two worlds");
        });
    }

    /** «Already in the overworld» is true only in the overworld; in the Nether with no portal to go out by it is an honest failure. */
    @GameTest(template = "tall_tree", timeoutTicks = 1000, batch = "errand10")
    public static void netherBackIsAlreadyHomeOnlyInTheOverworld(GameTestHelper h) {
        ground(h);
        lightPortal(h);
        var home = spawn(h, 6, 16).startWork("nether_back", "", 1);
        h.assertTrue(home.success() && home.message().contains("верхнем мире"), "In the overworld: " + home.message());
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = arenaCell(h);
        arena(nether, cell[0], cell[1]);
        noPortalsNear(nether, cell);
        CompanionEntity there = restored(h, nether, cell, null, -1);
        boolean[] added = {false};
        h.onEachTick(() -> {
            if (added[0] || !forcedLoaded(nether)) return;
            nether.addFreshEntity(there);
            var back = there.startWork("nether_back", "", 1);
            h.assertTrue(!back.success() && !back.message().contains("уже в верхнем") && !there.hasActiveWorkOrder(), "In the Nether with no portal: " + back.success() + " " + back.message());
            added[0] = true;
            h.succeed();
        });
    }

    /** No lit portal within his reach, the leftovers of other tests' arenas (they overlap in the Nether) included. */
    static void noPortalsNear(ServerLevel nether, int[] cell) {
        for (BlockPos pos : BlockPos.betweenClosed(cell[0] - 70, 140, cell[1] - 70, cell[0] + 70, 165, cell[1] + 70))
            if (nether.hasChunkAt(pos) && nether.getBlockState(pos).is(Blocks.NETHER_PORTAL)) nether.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    static int[] arenaCell(GameTestHelper h) {
        BlockPos at = h.absolutePos(new BlockPos(15, GROUND + 2, 16));
        return new int[] {Math.floorDiv(at.getX(), 8), Math.floorDiv(at.getZ(), 8)};
    }

    private static int broken(ServerLevel nether, BlockPos[] ores) {
        int left = 0;
        for (BlockPos ore : ores) if (nether.getBlockState(ore).is(Blocks.NETHER_QUARTZ_ORE)) left++;
        return ores.length - left;
    }

    /**
     * The companion as a loaded world gives him back: an entity of the Nether made out of saved data that carries the errand (the portal at
     * home, the exit portal if any, a deadline {@code lasts} ticks away; a negative {@code lasts}: no record at all), not yet added to the world.
     * Born in the overworld first, as the others.
     */
    static CompanionEntity restored(GameTestHelper h, ServerLevel nether, int[] cell, Long exit, long lasts) {
        CompanionEntity old = spawn(h, 6, 16);
        old.setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        CompoundTag tag = new CompoundTag();
        old.addAdditionalSaveData(tag);
        CompoundTag errand = new CompoundTag();
        errand.putLong("Home", h.absolutePos(new BlockPos(15, GROUND + 2, 16)).asLong());
        if (exit != null) errand.putLong("Exit", exit);
        errand.putLong("Since", nether.getGameTime());
        errand.putLong("Deadline", nether.getGameTime() + lasts);
        if (lasts >= 0) tag.put("Errand", errand);
        UUID id = old.getUUID();
        old.discard();
        CompanionEntity there = AICompanionMod.COMPANION.get().create(nether);
        there.setUUID(id);
        there.moveTo(cell[0] + 1.5, 151, cell[1] + 4.5, 0, 0);
        there.readAdditionalSaveData(tag);
        return there;
    }

    static String describe(GameTestHelper h, ServerLevel nether, UUID id) {
        var over = h.getLevel().getEntity(id);
        var there = nether.getEntity(id);
        return "overworld " + (over == null ? "none" : over.blockPosition()) + ", nether " + (there == null ? "none" : there.blockPosition()
                + " " + ((CompanionEntity) there).getLastTaskResult() + " [" + ((CompanionEntity) there).watchDetail() + "]");
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity c, String json) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-nether-errand", () -> false));
    }

    /**
     * A shut box above the Nether's roof where the game will look for the portal he comes out of (the scaled place, up to sixteen blocks
     * away it takes an existing one): a bedrock shell, a lit portal in the middle of it, six quartz ores in the open on both sides.
     */
    static BlockPos[] arena(ServerLevel nether, int cx, int cz) {
        int floor = 149;
        for (int x = cx - 8; x <= cx + 8; x += 8) for (int z = cz - 8; z <= cz + 8; z += 8) {
            var chunk = new ChunkPos(new BlockPos(x, floor, z));
            if (!FORCED.contains(chunk)) { FORCED.add(chunk); nether.setChunkForced(chunk.x, chunk.z, true); }
            nether.getChunk(chunk.x, chunk.z);
        }
        for (int x = cx - 8; x <= cx + 8; x++) for (int z = cz - 8; z <= cz + 8; z++) for (int y = floor; y <= floor + 9; y++) {
            boolean shell = x == cx - 8 || x == cx + 8 || z == cz - 8 || z == cz + 8 || y == floor || y == floor + 9;
            nether.setBlockAndUpdate(new BlockPos(x, y, z), (shell ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState());
        }
        // The frame faces north and south, four wide and five tall, its floor on the floor of the box; the portal two wide and three tall.
        for (int x = cx - 1; x <= cx + 2; x++) for (int y = floor + 1; y <= floor + 5; y++)
            if (x == cx - 1 || x == cx + 2 || y == floor + 1 || y == floor + 5) nether.setBlockAndUpdate(new BlockPos(x, y, cz), Blocks.OBSIDIAN.defaultBlockState());
        for (int x = cx; x <= cx + 1; x++) for (int y = floor + 2; y <= floor + 4; y++)
            nether.setBlockAndUpdate(new BlockPos(x, y, cz), Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X));
        List<BlockPos> ores = new ArrayList<>();
        for (int i = 0; i < 3; i++) { ores.add(new BlockPos(cx - 4 + i, floor + 1, cz - 5)); ores.add(new BlockPos(cx + 3 + i, floor + 1, cz + 5)); }
        for (BlockPos ore : ores) nether.setBlockAndUpdate(ore, Blocks.NETHER_QUARTZ_ORE.defaultBlockState());
        return ores.toArray(new BlockPos[0]);
    }

    static void ground(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND + 8; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31;
            h.setBlock(new BlockPos(x, y, z), y <= GROUND ? Blocks.BEDROCK : border ? Blocks.BEDROCK : Blocks.AIR);
        }
    }

    /** The frame as a player leaves it, lit with fire: the portal blocks and the game's record of them (the one he looks for). */
    static void lightPortal(GameTestHelper h) {
        for (int x = 14; x <= 17; x++) for (int y = GROUND + 1; y <= GROUND + 5; y++)
            if (x == 14 || x == 17 || y == GROUND + 1 || y == GROUND + 5) h.setBlock(new BlockPos(x, y, 16), Blocks.OBSIDIAN);
        h.setBlock(new BlockPos(15, GROUND + 2, 16), Blocks.FIRE);
        h.assertBlockPresent(Blocks.NETHER_PORTAL, new BlockPos(16, GROUND + 4, 16));
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int z) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), x, GROUND + 1, z);
        Player owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(x + 1, GROUND + 1, z + 1));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion((net.minecraft.server.level.ServerPlayer) owner);
        companion.setSearchRadius(16);
        return companion;
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        if (c.getMainHandItem().is(item)) count += c.getMainHandItem().getCount();
        return count;
    }
}

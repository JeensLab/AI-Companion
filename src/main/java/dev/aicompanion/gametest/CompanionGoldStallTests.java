package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionPillar;
import dev.aicompanion.entity.CompanionReach;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The gold order of the owner's own game (27.11): the ore at the top of a column was broken, its drop fell to the ground below, and he
 * stood on the column for half a minute («олег завис тупит стоит на своем столбе, золото внизу не подбирает»); the next ore three up
 * and nine across was refused as «not proven: no route»; the feet under his own column were refused as «над опорой не хватает
 * свободного места»; and the task line said sixteen ore blocks while the report said «накопал только 3 из 10».
 *
 * <p>Every row here is the player's own phrase through his own router and proves one rule: a drop below his feet while his own column
 * stands is taken from the ground at once (no half-minute wait), and a drop the player took holds nothing; a close block a column or a
 * floor reaches is no refusal over a walk that did not prove its miss; his own blocks are no obstruction for a way; and the ore order
 * says one number — the promise — on the task line, in the work and in the report.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGoldStallTests {
    private static final int SIDE = 48, TOP = 32, GROUND = 5, FEET = GROUND + 1;
    private static final int HOME_X = 20, HOME_Z = 16;

    /**
     * An ore hanging seven over the floor where no stand cell reaches it: a column of his own soil is the way up, and its drop falls to
     * the floor below his feet. A second ore he still has to come at keeps the mine going, as in the owner's game. The drop is taken
     * within a few seconds of coming to rest — not after the half-minute the chase was held off for while his own column stood.
     */
    @GameTest(template = "way_wide", timeoutTicks = 1200)
    public static void theDropBelowHisColumnIsTakenFromTheGroundAtOnce(GameTestHelper h) {
        flat(h, Blocks.STONE);
        // The ore hangs seven over the floor, and the floor of the ring one across from it is dug out: the column to it is raised two
        // across from the ore, so the drop falls beside him to the floor instead of past his head.
        BlockPos high = new BlockPos(HOME_X + 3, FEET + 7, HOME_Z);
        h.setBlock(high, Blocks.GOLD_ORE);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            h.setBlock(new BlockPos(HOME_X + 3 + dx, GROUND, HOME_Z + dz), Blocks.AIR);
        }
        h.setBlock(new BlockPos(HOME_X + 6, FEET + 1, HOME_Z + 4), Blocks.GOLD_ORE);
        // A third ore farther off: the mine goes on after the drop fell below the column, as in the owner's game.
        h.setBlock(new BlockPos(HOME_X - 12, FEET + 1, HOME_Z - 12), Blocks.GOLD_ORE);
        CompanionEntity c = spawn(h, HOME_X, FEET, HOME_Z);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 16));
        long[] restAt = {-1}, takenAt = {-1};
        int[] top = {0};
        int[] bagAtRest = {0}, supportsWhenTaken = {-1};
        h.onEachTick(() -> {
            top[0] = Math.max(top[0], c.getTemporarySupportCount());
            if (restAt[0] < 0 && goldBelow(h, c) != null) { restAt[0] = h.getTick(); bagAtRest[0] = count(c, Items.RAW_GOLD); }
            if (restAt[0] >= 0 && takenAt[0] < 0 && count(c, Items.RAW_GOLD) > bagAtRest[0]) {
                takenAt[0] = h.getTick();
                supportsWhenTaken[0] = c.getTemporarySupportCount();
            }
        });
        var future = say(h, c, "добудь золота");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.RAW_GOLD) >= 1, "The drop below his column is not in the bag: " + future.join().report());
            h.assertTrue(top[0] > 0, "No column was raised to the ore over him: " + journal(c, "way_decision"));
            h.assertTrue(restAt[0] >= 0 && takenAt[0] >= 0, "No drop was seen at rest and taken: " + restAt[0] + "/" + takenAt[0]);
            h.assertTrue(takenAt[0] - restAt[0] <= 200, "The drop below his feet waited " + (takenAt[0] - restAt[0])
                    + " ticks on the column: " + journal(c, "work_stalled") + journal(c, "pickup_way"));
            h.assertTrue(supportsWhenTaken[0] == 0, "The drop was taken while his column still stood under him: "
                    + supportsWhenTaken[0] + " " + journal(c, "pickup_way"));
            h.assertTrue(c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports(), "His column still stands: " + c.getTemporarySupportCount());
        });
    }

    /**
     * The owner's own stall, as it happened: the column of his own soil stands to an ore seven over the floor, the drop of that
     * ore falls to the floor below his feet, and two more ore blocks still wait — so the promise is three and more than one block
     * is still to take when the drop lies there. At 15593491 the work stood on that column over the drop for the pickup's whole
     * patience: the drop was taken only after the half-minute hold, and the column came down with it. Here the drop is taken from
     * the ground within a few seconds of coming to rest, while the column still stood, and the column comes down after it, its soil
     * back in his bag, and the order goes on to the other two ores.
     */
    @GameTest(template = "way_wide", timeoutTicks = 2400)
    public static void theDropBelowAStandingColumnIsTakenAtOnce(GameTestHelper h) {
        flat(h, Blocks.STONE);
        // The ore hangs seven over the floor and three across: no stand cell reaches it, a column of his own soil does. The floor of
        // the ring one across from it is dug out, so the column stands two across and its drop falls beside him, not past his head.
        BlockPos high = new BlockPos(HOME_X + 3, FEET + 7, HOME_Z);
        h.setBlock(high, Blocks.GOLD_ORE);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            h.setBlock(new BlockPos(HOME_X + 3 + dx, GROUND, HOME_Z + dz), Blocks.AIR);
        }
        // Two more ores, farther off, so the promise is three and two blocks still wait when the drop falls below the column.
        h.setBlock(new BlockPos(HOME_X - 12, FEET + 1, HOME_Z - 12), Blocks.GOLD_ORE);
        h.setBlock(new BlockPos(HOME_X - 13, FEET + 1, HOME_Z + 11), Blocks.GOLD_ORE);
        CompanionEntity c = spawn(h, HOME_X, FEET, HOME_Z);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 16));
        long[] restAt = {-1}, takenAt = {-1};
        int[] top = {0}, stoodAtRest = {-1}, bagAtRest = {0}, supportsWhenTaken = {-1};
        h.onEachTick(() -> {
            top[0] = Math.max(top[0], c.getTemporarySupportCount());
            if (restAt[0] < 0 && goldBelow(h, c) != null) {
                restAt[0] = h.getTick(); stoodAtRest[0] = c.getTemporarySupportCount(); bagAtRest[0] = count(c, Items.RAW_GOLD);
            }
            if (restAt[0] >= 0 && takenAt[0] < 0 && count(c, Items.RAW_GOLD) > bagAtRest[0]) {
                takenAt[0] = h.getTick(); supportsWhenTaken[0] = c.getTemporarySupportCount();
                dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), "stall_proof", "entity", c.getUUID(),
                        "rest", restAt[0], "taken", takenAt[0], "waited", takenAt[0] - restAt[0],
                        "stoodAtRest", stoodAtRest[0], "supportsAtTaken", supportsWhenTaken[0]);
            }
        });
        List<List<PlannedAction>> plans = new ArrayList<>();
        var future = say(h, c, "добудь золота", plans);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(plans.stream().anyMatch(plan -> !plan.isEmpty() && plan.getFirst().count() > 1),
                    "The order promised a single ore: " + plans);
            h.assertTrue(top[0] > 0, "No column was raised to the ore over him: " + journal(c, "way_decision"));
            h.assertTrue(restAt[0] >= 0, "No drop was seen at rest below his feet: " + journal(c, "pickup_way"));
            h.assertTrue(stoodAtRest[0] > 0, "The column was already down when the drop came to rest: " + stoodAtRest[0]
                    + " " + journal(c, "pickup_way"));
            h.assertTrue(takenAt[0] >= 0, "The drop below the column was never taken: " + report);
            h.assertTrue(takenAt[0] - restAt[0] <= 200, "The drop below his column waited " + (takenAt[0] - restAt[0])
                    + " ticks on it: " + journal(c, "work_stalled") + journal(c, "pickup_way"));
            h.assertTrue(supportsWhenTaken[0] == 0, "The drop was taken while his column still stood under him: "
                    + supportsWhenTaken[0] + " " + journal(c, "pickup_way"));
            h.assertTrue(c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports(), "His column still stands: " + c.getTemporarySupportCount());
            h.assertTrue(count(c, Items.DIRT) == 16, "The soil of the column is not back in the bag: " + count(c, Items.DIRT));
            h.assertTrue(count(c, Items.RAW_GOLD) == 3, "The order did not go on to the other two ores: "
                    + count(c, Items.RAW_GOLD) + " " + report);
        });
    }

    /**
     * The same column and the same drop below it, but the player takes the drop himself, as the owner does: a drop that is no longer
     * his is no reason to stand on the column — the work goes on to the other two ores at once, and the column comes down with its
     * soil in the bag. Nothing waits for the drop the player carried off.
     */
    @GameTest(template = "way_wide", timeoutTicks = 2400)
    public static void theDropThePlayerTookDoesNotHoldTheWork(GameTestHelper h) {
        flat(h, Blocks.STONE);
        BlockPos high = new BlockPos(HOME_X + 3, FEET + 7, HOME_Z);
        h.setBlock(high, Blocks.GOLD_ORE);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            h.setBlock(new BlockPos(HOME_X + 3 + dx, GROUND, HOME_Z + dz), Blocks.AIR);
        }
        h.setBlock(new BlockPos(HOME_X - 12, FEET + 1, HOME_Z - 12), Blocks.GOLD_ORE);
        h.setBlock(new BlockPos(HOME_X - 13, FEET + 1, HOME_Z + 11), Blocks.GOLD_ORE);
        CompanionEntity c = spawn(h, HOME_X, FEET, HOME_Z);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 16));
        var owner = (net.minecraft.server.level.ServerPlayer) h.getLevel().getPlayerByUUID(c.getOwnerId());
        long[] tookAt = {-1}, nextAt = {-1};
        int[] bagWhenTook = {0};
        h.onEachTick(() -> {
            if (tookAt[0] >= 0) {
                if (nextAt[0] < 0 && count(c, Items.RAW_GOLD) > bagWhenTook[0]) nextAt[0] = h.getTick();
                return;
            }
            ItemEntity drop = goldBelow(h, c);
            if (drop == null || drop.hasPickUpDelay()) return;
            owner.teleportTo(drop.getX(), drop.getY(), drop.getZ());
            drop.playerTouch(owner);
            tookAt[0] = h.getTick(); bagWhenTook[0] = count(c, Items.RAW_GOLD);
        });
        var future = say(h, c, "добудь золота");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(tookAt[0] >= 0, "The player never took the drop below the column: " + report);
            h.assertTrue(owner.getInventory().countItem(Items.RAW_GOLD) >= 1, "The drop is not in the player's bag: "
                    + owner.getInventory().countItem(Items.RAW_GOLD));
            h.assertTrue(nextAt[0] >= 0 && nextAt[0] - tookAt[0] <= 300, "The work waited on the drop the player took: "
                    + (nextAt[0] < 0 ? "no next ore" : (nextAt[0] - tookAt[0]) + " ticks") + " " + report);
            h.assertTrue(count(c, Items.RAW_GOLD) == 2, "The two ores left were not mined: " + count(c, Items.RAW_GOLD) + " " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports(), "His column still stands: " + c.getTemporarySupportCount());
            h.assertTrue(count(c, Items.DIRT) == 16, "The soil of the column is not back in the bag: " + count(c, Items.DIRT));
        });
    }

    /**
     * His own column of four stands under him (his own soil, taken up), and the next ore hangs six over his feet in a one-cell
     * chimney: no stand cell reaches it, and the only foot a column to it could stand at is the cell his own column stands on. The
     * way must still say a column is there — his own blocks are no obstruction — and the column grows from four to seven on them.
     */
    @GameTest(template = "way_wide", timeoutTicks = 900)
    public static void theFeetUnderHisOwnColumnAreFree(GameTestHelper h) {
        flat(h, Blocks.STONE);
        // A one-cell chimney: the only standable cell about him is the one his own column stands on.
        for (int y = FEET; y <= FEET + 14; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            h.setBlock(new BlockPos(HOME_X + dx, y, HOME_Z + dz), Blocks.STONE);
        }
        // A block he set down himself, three over his feet, stands in the rise of the column to the ore above it.
        BlockPos own = new BlockPos(HOME_X, FEET + 3, HOME_Z);
        h.setBlock(own, Blocks.DIRT);
        // The block seven over the floor: a column of four of his own soil, which he raises through his own block and stands on.
        BlockPos first = new BlockPos(HOME_X, FEET + 7, HOME_Z);
        h.setBlock(first, Blocks.GOLD_ORE);
        // The next, six over the top of that column: no stand cell reaches it, and its only feet are on his own blocks.
        BlockPos next = new BlockPos(HOME_X, FEET + 10, HOME_Z);
        h.setBlock(next, Blocks.GOLD_ORE);
        CompanionEntity c = spawn(h, HOME_X, FEET, HOME_Z);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 16));
        c.rememberPlaced(h.absolutePos(own));
        BlockPos firstAt = h.absolutePos(first), nextAt = h.absolutePos(next);
        int[] steps = {0};
        boolean[] climbed = {false};
        boolean[] descended = {false};
        String[] how = {""};
        String[] last = {""};
        h.onEachTick(() -> {
            if (!climbed[0]) {
                CompanionPillar.Outcome outcome = c.temporaryPillar().tickToward(firstAt, false, CompanionPillar.WAY_SUPPORTS);
                last[0] = outcome + "/" + c.temporaryPillar().failureReason() + "/" + c.temporaryPillar().isActive() + "/" + c.getTemporarySupportCount();
                if (c.getTemporarySupportCount() == 4) {
                    climbed[0] = true;
                    steps[0] = c.temporaryPillar().leastStepsNear(nextAt, CompanionPillar.WAY_SUPPORTS);
                    var answer = CompanionReach.how(c, h.getLevel(), nextAt, 3.5D, true, false, CompanionPillar.WAY_SUPPORTS);
                    how[0] = answer.way() + "/" + answer.steps() + "/" + answer.why();
                    // The column comes down as the recovery goal walks it down, block by block.
                    c.temporaryPillar().requestCleanup();
                }
                return;
            }
            if (descended[0]) return;
            c.temporaryPillar().tickCleanup();
            descended[0] = c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports();
        });
        h.succeedWhen(() -> {
            h.assertTrue(climbed[0], "The column of four was never raised: " + last[0] + " "
                    + journal(c, "way_climb") + journal(c, "way_foot") + journal(c, "search_not_started") + journal(c, "search_skipped"));
            h.assertTrue(steps[0] > 0, "No column was found to the ore over his own column: " + steps[0] + " " + how[0]
                    + " " + journal(c, "way_decision") + journal(c, "way_foot"));
            h.assertTrue(!how[0].startsWith("NONE"), "The way does not say a column is there: " + how[0]);
            // The soil of the column — his own block among it — is back in the bag, and no cell of it is left standing.
            h.assertTrue(descended[0], "The column did not come down: " + c.getTemporarySupportCount() + " " + last[0]);
            h.assertTrue(count(c, Items.DIRT) == 16, "The soil of the column is not back in the bag: " + count(c, Items.DIRT));
        });
    }

    /**
     * A close block the walk cannot come at: a trench two cells across, from bank to bank, and the ore on the far bank four across.
     * Every search here spends its whole budget on its first cell, so the miss is not proven — and a floor across the gap is still
     * the way to it, never «not proven: no route» (the owner, 27.11: an ore three up and nine across was refused that way). The ore
     * ends in the bag, on a floor of his own cobblestone.
     */
    @GameTest(template = "way_wide", timeoutTicks = 2400)
    public static void aCloseTargetTheWalkCannotProveIsStillReached(GameTestHelper h) {
        flat(h, Blocks.STONE);
        // A trench two cells across, from bank to bank: no walk crosses it, and the ore stands on the far bank four across — a floor
        // laid over the gap is the way to it, and the ore itself ends in the bag.
        for (int x = HOME_X + 2; x <= HOME_X + 4; x++)
            for (int z = 1; z <= SIDE - 2; z++) for (int y = 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        BlockPos ore = new BlockPos(HOME_X + 5, FEET + 1, HOME_Z);
        h.setBlock(ore, Blocks.GOLD_ORE);
        CompanionEntity c = spawn(h, HOME_X, FEET, HOME_Z);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.COBBLESTONE, 16));
        // Every search of this test is cut short: a miss that did not see the whole region for its cells proves nothing.
        if (c.getNavigation() instanceof dev.aicompanion.entity.CompanionNavigation nav) nav.limitSearch(5);
        var future = say(h, c, "добудь золота");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(journal(c, "way_refused").stream().noneMatch(line -> line.contains("not proven")),
                    "The close ore was refused as not proven: " + journal(c, "way_refused"));
            h.assertTrue(count(c, Items.RAW_GOLD) >= 1, "The close ore is not in the bag: " + future.join().report());
            // The floor over the trench was laid from his own cobblestone, and it is what the walk to the ore crossed.
            h.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(HOME_X + 2, GROUND, HOME_Z));
            h.assertTrue(count(c, Items.COBBLESTONE) <= 14, "The floor was laid out of nothing: " + count(c, Items.COBBLESTONE));
            h.assertBlockPresent(Blocks.AIR, ore);
        });
    }

    /**
     * «Добудь золота» without a number is sixteen on the plan, and ten ore blocks stand about him: the task line (what the tasks window
     * says of the first step), the promise the work holds and the report all say ten — one honest number, never sixteen over ten.
     */
    @GameTest(template = "way_wide", timeoutTicks = 2400)
    public static void anOreOrderSaysOneNumberEverywhere(GameTestHelper h) {
        flat(h, Blocks.STONE);
        int placed = 0;
        for (int x = HOME_X - 3; x <= HOME_X + 3 && placed < 10; x++)
            for (int z = HOME_Z - 3; z <= HOME_Z + 3 && placed < 10; z++) {
                if (x == HOME_X && z == HOME_Z) continue;
                h.setBlock(new BlockPos(x, FEET + 1, z), Blocks.GOLD_ORE);
                placed++;
            }
        h.assertTrue(placed == 10, "Ten ores were not placed: " + placed);
        CompanionEntity c = spawn(h, HOME_X, FEET, HOME_Z);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 16));
        List<List<PlannedAction>> plans = new ArrayList<>();
        var future = say(h, c, "добудь золота", plans);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(!plans.isEmpty() && !plans.getLast().isEmpty(), "No step was ever started: " + plans);
            String line = CompanionDialogue.describe(plans.getLast().getFirst());
            h.assertTrue(line.contains("(10 бл. руды)"), "The task line does not say ten: " + line + " " + journal(c, "ore_promise"));
            h.assertTrue(c.getLastTaskResult().contains("из 10") || future.join().report().contains("из 10")
                    || future.join().report().contains("10 блоков"), "The promise and the report do not say ten: "
                    + c.getLastTaskResult() + " | " + future.join().report());
            h.assertTrue(!line.contains("16") && !future.join().report().contains("из 16"), "Sixteen is still said somewhere: "
                    + line + " | " + future.join().report());
        });
    }

    // ---- helpers ----

    private static CompletableFuture<CompanionPlanRunner.Outcome> say(GameTestHelper h, CompanionEntity c, String phrase) {
        return say(h, c, phrase, null);
    }

    /** The phrase through the player's own router, with the plans the runner pushed (what the tasks window shows) recorded. */
    private static CompletableFuture<CompanionPlanRunner.Outcome> say(GameTestHelper h, CompanionEntity c, String phrase,
                                                                    List<List<PlannedAction>> plans) {
        String command = CompanionLocalCommands.route(phrase, "Олег", RU);
        h.assertTrue(command != null, "«" + phrase + "» went to the model");
        var port = new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-gold-stall", () -> false,
                (plan, index) -> { if (plans != null) plans.add(List.copyOf(plan)); });
        return CompanionPlanRunner.run(CompanionLocalCommands.actions(command), port);
    }

    /** Whether a drop of raw gold lies at rest about him: what the pickup is left with once the ore is broken. */
    private static boolean goldOnTheFloor(GameTestHelper h, CompanionEntity c) {
        AABB box = new AABB(c.blockPosition()).inflate(10, 6, 10);
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, box, found -> found.getItem().is(Items.RAW_GOLD) && found.onGround())
                .stream().findFirst().isPresent();
    }

    /** The drop of raw gold at rest below his feet: what fell below the column he stands on. Null when none lies there. */
    private static ItemEntity goldBelow(GameTestHelper h, CompanionEntity c) {
        AABB box = new AABB(c.blockPosition()).inflate(10, 12, 10);
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, box,
                        found -> found.getItem().is(Items.RAW_GOLD) && found.onGround() && found.blockPosition().getY() < c.blockPosition().getY())
                .stream().findFirst().orElse(null);
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int y, int z) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), x, y, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(x - 2, FEET, z));
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

    /** The Russian names a client shows for the things these rows use; the test server runs en_us. */
    private static final java.util.Map<String, String> RU_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry("gold_ore", "Золотая руда"),
            java.util.Map.entry("raw_gold", "Сырое золото"));

    private static final Function<Item, String> RU = CompanionGoldStallTests::russianName;

    private static String russianName(Item item) {
        String name = RU_NAMES.get(BuiltInRegistries.ITEM.getKey(item).getPath());
        return name != null ? name : new ItemStack(item).getHoverName().getString();
    }
}

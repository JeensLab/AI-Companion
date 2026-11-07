package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionParkLink;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAutonomy;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionGoals;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Issue #105: «стой»/«ко мне»/«защищай» in the middle of a dig is an urgent order, not the end of the dig. He puts the
 * rest of the work aside, does the order, and digs exactly what was left once he is free; a second work order meanwhile is refused
 * by name of the deed. The park is local to the world it was made in, and an urgent cancel of the queue parks like the order itself.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionUrgentOrderTests {
    private static final int WANTED = 6;
    /** Stone in the vein beyond the order: an over-dig shows as a block missing at the end. */
    private static final int EXTRA = 4;
    private static final double ARRIVED = 3.0D;
    /** Ticks a plain cancel is watched for a dig that should not come back. */
    private static final int PLAIN_WATCH_TICKS = 60;
    private static final int RELOAD_WATCH_TICKS = 40;
    /** Ticks after an order in which a park would show. */
    private static final int ORDER_WATCH_TICKS = 10;
    /** Ticks he stands on «стой здесь» before «за мной». */
    private static final int STAY_TICKS = 30;
    /** Ticks after a park at which an order that was slow to reach the server arrives: past the old grace of 20, inside the new one. */
    private static final int LATE_ORDER_TICKS = 40;
    private static final int WOOD_WANTED = 8;
    private static final int TREE_HEIGHT = 6;

    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void comeInterruptsStoneAndTheRestIsDugOnce(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        vein(helper);
        ServerPlayer player = owner(helper, companion);
        boolean[] called = {false};
        double[] minDist = {Double.MAX_VALUE};
        int[] left = {WANTED + EXTRA};
        helper.onEachTick(() -> {
            int now = veinLeft(helper);
            helper.assertTrue(now >= left[0] || !companion.isFollowing() || companion.isWorking(), "A block was dug while he was only following");
            left[0] = now;
            if (!called[0] && count(companion, Items.COBBLESTONE) >= 2) {
                called[0] = true;
                CompanionEvents.executeAction(player, "come", "", 1);
            }
            if (called[0]) minDist[0] = Math.min(minDist[0], companion.distanceTo(player));
        });
        helper.assertTrue(companion.startWork("gather_stone", "", WANTED).success(), "Stone work was not accepted");
        helper.succeedWhen(() -> {
            helper.assertTrue(called[0], "The dig has not gone far enough for the order yet");
            helper.assertTrue(minDist[0] <= ARRIVED, "He did not come to the player: closest " + minDist[0]);
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(count(companion, Items.COBBLESTONE) == WANTED, "The rest of the dig was not done once: " + count(companion, Items.COBBLESTONE));
            helper.assertTrue(veinLeft(helper) == EXTRA, "Over-dug or under-dug: " + veinLeft(helper) + " blocks left, expected " + EXTRA);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void reloadMidParkResumesTheRest(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        vein(helper);
        ServerPlayer player = owner(helper, companion);
        CompanionEntity[] current = {companion};
        int[] stage = {0};
        helper.onEachTick(() -> {
            if (stage[0] != 0 || count(companion, Items.COBBLESTONE) < 2) return;
            stage[0] = 1;
            CompanionEvents.executeAction(player, "come", "", 1);
            int dug = count(companion, Items.COBBLESTONE);
            CompoundTag data = new CompoundTag();
            companion.saveWithoutId(data);
            helper.assertTrue("gather_stone".equals(data.getString("ParkedAction")), "The rest of the dig was not saved: " + data.getString("ParkedAction"));
            helper.assertTrue(data.getInt("ParkedCount") == WANTED - dug, "Saved remainder " + data.getInt("ParkedCount") + " after " + dug);
            companion.discard();
            helper.runAfterDelay(1, () -> {
                CompanionEntity replacement = AICompanionMod.COMPANION.get().create(helper.getLevel());
                replacement.load(data);
                helper.getLevel().addFreshEntity(replacement);
                current[0] = replacement;
                stage[0] = 2;
            });
        });
        helper.assertTrue(companion.startWork("gather_stone", "", WANTED).success(), "Stone work was not accepted");
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] == 2, "Not reloaded yet");
            CompanionEntity loaded = current[0];
            done(helper, loaded);
            helper.assertTrue(count(loaded, Items.COBBLESTONE) == WANTED, "The rest was not dug after the reload: " + count(loaded, Items.COBBLESTONE));
            helper.assertTrue(veinLeft(helper) == EXTRA, "Over-dug or under-dug: " + veinLeft(helper) + " blocks left, expected " + EXTRA);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void secondWorkOrderIsRefusedNamingTheDeed(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        vein(helper);
        helper.assertTrue(companion.startWork("gather_stone", "", 5).success(), "Stone work was not accepted");
        var refused = companion.startWork("gather_wood", "", 2);
        helper.assertTrue(!refused.success() && refused.message().startsWith("Погоди, доделаю добычу камня"),
                "Refusal does not name the deed: " + refused.message());
        helper.assertTrue(companion.isWorking(), "The refusal stopped the stone dig");
        helper.succeed();
    }

    /** Summon into another world, a portal, a save loaded elsewhere: the rest of a dig of one world is not dug in another. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void parkFromAnotherDimensionIsDroppedQuietly(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        vein(helper);
        ServerPlayer player = owner(helper, companion);
        CompanionEntity[] current = {companion};
        int[] stage = {0, 0}; // stage, vein blocks left at the reload
        long[] reloadedAt = {0};
        helper.onEachTick(() -> {
            if (stage[0] != 0 || count(companion, Items.COBBLESTONE) < 2) return;
            stage[0] = 1;
            CompanionEvents.executeAction(player, "come", "", 1);
            CompoundTag data = new CompoundTag();
            companion.saveWithoutId(data);
            helper.assertTrue("gather_stone".equals(data.getString("ParkedAction")), "The rest of the dig was not saved: " + data.getString("ParkedAction"));
            data.putString("ParkedDimension", "minecraft:the_nether");
            companion.discard();
            stage[1] = veinLeft(helper);
            helper.runAfterDelay(1, () -> {
                CompanionEntity replacement = AICompanionMod.COMPANION.get().create(helper.getLevel());
                replacement.load(data);
                helper.getLevel().addFreshEntity(replacement);
                current[0] = replacement;
                reloadedAt[0] = helper.getTick();
                stage[0] = 2;
            });
        });
        helper.assertTrue(companion.startWork("gather_stone", "", WANTED).success(), "Stone work was not accepted");
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] == 2 && helper.getTick() - reloadedAt[0] >= RELOAD_WATCH_TICKS, "Not reloaded long enough yet");
            CompanionEntity loaded = current[0];
            helper.assertTrue(!loaded.isWorking() && !loaded.isGatheringTask(), "He went on with the dig of another world: " + loaded.getLastTaskResult());
            CompoundTag after = new CompoundTag();
            loaded.saveWithoutId(after);
            helper.assertTrue(!after.contains("ParkedAction"), "The park of another world is kept: " + after.getString("ParkedAction"));
            helper.assertTrue(veinLeft(helper) == stage[1], "A block was dug: " + stage[1] + " -> " + veinLeft(helper));
        });
    }

    /** The queue's urgent cancel (what «иди сюда» sends) parks the dig; the come that follows leaves the park alone and the rest is dug once. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void urgentQueueCancelParksAndTheRestIsDugOnce(GameTestHelper helper) {
        queuedDigThenCancel(helper, true, false);
    }

    /** A plain cancel («стоп») through the same queue stops the dig for good: nothing is parked and nothing comes back. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void plainQueueCancelStopsTheDigWithoutAPark(GameTestHelper helper) {
        queuedDigThenCancel(helper, false, false);
    }

    /** A goal paused by an earlier player order must not swallow the park when the come order runs its own stop of the work. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void pausedGoalDoesNotEatThePark(GameTestHelper helper) {
        queuedDigThenCancel(helper, true, true);
    }

    /** The server entry of «come» parks the dig before the paused goal's own stop can end it. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void aServerEntryComeWithAPausedGoalParksTheDig(GameTestHelper helper) {
        Scene scene = digScene(helper, true);
        Watch watch = new Watch();
        watchOrder(helper, scene, watch, true, () -> CompanionEvents.executeAction(scene.player(), "come", "", 1));
        finishWhen(helper, scene, watch, true);
    }

    /** The dig of a deed of his own is no order of the player: «ко мне» ends the deed and nothing is put aside to be resumed. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void comeDuringADeedsDigParksNothing(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.OAK_LOG, CompanionAutonomy.WOOD_STOCK));
        vein(helper);
        ServerPlayer player = owner(helper, companion);
        companion.autonomy().setEnabled(true);
        long[] orderedAt = {-1};
        helper.onEachTick(() -> {
            if (orderedAt[0] < 0) {
                if (!companion.autonomy().running() || count(companion, Items.COBBLESTONE) < 1) return;
                orderedAt[0] = helper.getTick();
                CompanionEvents.executeAction(player, "come", "", 1);
                return;
            }
            CompoundTag data = new CompoundTag();
            companion.saveWithoutId(data);
            helper.assertTrue(!data.contains("ParkedAction"), "The dig of his deed was put aside as an order: " + data.getString("ParkedAction"));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(orderedAt[0] >= 0, "The deed has not dug a block yet");
            helper.assertTrue(helper.getTick() - orderedAt[0] >= ORDER_WATCH_TICKS, "Watching for a park");
            helper.assertTrue(!companion.autonomy().running(), "The deed went on after the order");
        });
    }

    /** «/companion stay» with a paused goal: the goal's own stop of the work comes after the park, and «за мной» digs what was left. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void stayWithAPausedGoalParksTheDigAndFollowResumesIt(GameTestHelper helper) {
        Scene scene = digScene(helper, true);
        Watch watch = new Watch();
        watchOrder(helper, scene, watch, true, () -> command(scene.player(), "companion stay"));
        helper.onEachTick(() -> {
            if (watch.orderedAt < 0 || helper.getTick() - watch.orderedAt != STAY_TICKS) return;
            helper.assertTrue(!scene.companion().isFollowing(), "He still follows after «стой здесь»");
            watch.arrived = true;
            command(scene.player(), "companion follow");
        });
        finishWhen(helper, scene, watch, true);
    }

    /** «Иди за мной» said on the way back to the player: the plain cancel of the walk leaves the rest of the dig for later. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void followOrderOnTheWayKeepsThePark(GameTestHelper helper) {
        Scene scene = digScene(helper, false);
        Watch watch = new Watch();
        watchOrder(helper, scene, watch, true, () -> comeUrgently(scene));
        onTheWay(helper, scene, watch, () -> {
            scene.link().cancelAll(false, false, false);
            command(scene.player(), "companion follow");
            watch.arrived = true;
        });
        finishWhen(helper, scene, watch, true);
    }

    /** The player's cancel on the way back ends the walk and the rest of the dig with it: nothing comes back. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void cancelWordOnTheWayDropsThePark(GameTestHelper helper) {
        Scene scene = digScene(helper, false);
        Watch watch = new Watch();
        long[] cancelledAt = {-1};
        int[] veinAtCancel = {-1};
        watchOrder(helper, scene, watch, true, () -> comeUrgently(scene));
        onTheWay(helper, scene, watch, () -> {
            CompoundTag parked = new CompoundTag();
            scene.companion().saveWithoutId(parked);
            helper.assertTrue(parked.contains("ParkedAction"), "Nothing was parked before the cancel");
            scene.link().cancelAll(false, true, false);
            watch.arrived = true;
            cancelledAt[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            if (cancelledAt[0] >= 0 && helper.getTick() - cancelledAt[0] == 2) veinAtCancel[0] = veinLeft(helper);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(cancelledAt[0] >= 0 && helper.getTick() - cancelledAt[0] >= PLAIN_WATCH_TICKS, "Watching the cancel");
            CompoundTag data = new CompoundTag();
            scene.companion().saveWithoutId(data);
            helper.assertTrue(!data.contains("ParkedAction"), "The cancel left the rest of the dig: " + data.getString("ParkedAction"));
            helper.assertTrue(!scene.companion().isWorking() && veinLeft(helper) == veinAtCancel[0], "He dug after the cancel");
        });
    }

    /** Runs {@code next} once, on the first tick the walk to the player is work on the server and he is still on his way. */
    private static void onTheWay(GameTestHelper helper, Scene scene, Watch watch, Runnable next) {
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0] || watch.orderedAt < 0 || helper.getTick() - watch.orderedAt < 3 || scene.companion().activeWorkId() == 0) return;
            done[0] = true;
            helper.assertTrue(scene.companion().distanceTo(scene.player()) > ARRIVED, "He has come already, nothing to interrupt");
            next.run();
        });
    }

    /** An order that reached the server slowly (up to a second in play) still finds him free of the dig: no second park, no second phrase. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void lateOrderFindsHimStillFreeOfTheDig(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        vein(helper);
        ServerPlayer player = owner(helper, companion);
        long[] parkedAt = {-1};
        boolean[] arrived = {false};
        helper.onEachTick(() -> {
            if (parkedAt[0] < 0) {
                if (count(companion, Items.COBBLESTONE) < 2) return;
                helper.assertTrue(companion.parkWork(), "The dig was not put aside");
                parkedAt[0] = helper.getTick();
                return;
            }
            long since = helper.getTick() - parkedAt[0];
            if (since < LATE_ORDER_TICKS) helper.assertTrue(!companion.isWorking(), "He went back to the dig before the order came, since=" + since);
            if (since == LATE_ORDER_TICKS) CompanionEvents.executeAction(player, "come", "", 1);
            if (since > LATE_ORDER_TICKS && companion.distanceTo(player) <= ARRIVED) arrived[0] = true;
        });
        helper.assertTrue(companion.startWork("gather_stone", "", WANTED).success(), "Stone work was not accepted");
        helper.succeedWhen(() -> {
            helper.assertTrue(parkedAt[0] >= 0 && arrived[0], "He has not come yet");
            done(helper, companion);
            helper.assertTrue(count(companion, Items.COBBLESTONE) == WANTED, "The rest was not dug once: " + count(companion, Items.COBBLESTONE));
            helper.assertTrue(veinLeft(helper) == EXTRA, "Over-dug or under-dug: " + veinLeft(helper) + " blocks left, expected " + EXTRA);
        });
    }

    /** A stone just broken lies on the ground when the order comes: he picks it up on the way, and it is not dug a second time. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void stoneOnTheGroundAtTheOrderIsNotDugTwice(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        vein(helper);
        ServerPlayer player = owner(helper, companion);
        int[] last = {WANTED + EXTRA};
        boolean[] ordered = {false, false}; // ordered, arrived
        helper.onEachTick(() -> {
            int now = veinLeft(helper);
            if (!ordered[0] && now < last[0] && count(companion, Items.COBBLESTONE) >= 1) {
                ordered[0] = true;
                CompanionEvents.executeAction(player, "come", "", 1);
            }
            last[0] = now;
            if (!ordered[0]) return;
            CompoundTag data = new CompoundTag();
            companion.saveWithoutId(data);
            if (data.contains("ParkedAction")) helper.assertTrue(data.getInt("ParkedCount") + count(companion, Items.COBBLESTONE) == WANTED,
                    "The rest and the bag do not add up to the order: " + data.getInt("ParkedCount") + " + " + count(companion, Items.COBBLESTONE));
            if (companion.distanceTo(player) <= ARRIVED) ordered[1] = true;
        });
        helper.assertTrue(companion.startWork("gather_stone", "", WANTED).success(), "Stone work was not accepted");
        helper.succeedWhen(() -> {
            helper.assertTrue(ordered[1], "He has not come yet");
            done(helper, companion);
            helper.assertTrue(count(companion, Items.COBBLESTONE) == WANTED, "Not the ordered amount: " + count(companion, Items.COBBLESTONE));
            helper.assertTrue(veinLeft(helper) == EXTRA, "Over-dug or under-dug: " + veinLeft(helper) + " blocks left, expected " + EXTRA);
        });
    }

    /** The same for wood: a log lying on the ground at the order is part of the order, so the rest and the bag add up to it. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void logOnTheGroundAtTheOrderIsCountedOnce(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        trees(helper);
        ServerPlayer player = owner(helper, companion);
        int[] last = {-1};
        boolean[] ordered = {false};
        int[] checked = {0};
        helper.onEachTick(() -> {
            int now = logBlocks(helper);
            boolean broken = last[0] >= 0 && now < last[0];
            last[0] = now;
            if (!ordered[0] && broken) {
                ordered[0] = true;
                CompanionEvents.executeAction(player, "come", "", 1);
            }
            if (!ordered[0]) return;
            CompoundTag data = new CompoundTag();
            companion.saveWithoutId(data);
            if (!data.contains("ParkedAction")) return;
            checked[0]++;
            helper.assertTrue(data.getInt("ParkedCount") + logsIn(companion) == WOOD_WANTED,
                    "The rest and the bag do not add up to the order: " + data.getInt("ParkedCount") + " + " + logsIn(companion));
        });
        helper.assertTrue(companion.startWork("gather_wood", "", WOOD_WANTED).success(), "Wood work was not accepted");
        helper.succeedWhen(() -> {
            helper.assertTrue(ordered[0] && checked[0] > 0, "The order did not park the wood: ordered=" + ordered[0] + ", logs=" + last[0]
                    + ", working=" + companion.isWorking() + ", bag=" + logsIn(companion) + ", " + companion.getLastTaskResult());
            done(helper, companion);
        });
    }

    /** «/companion summon» typed as a command is an urgent order like «иди ко мне»: the dig is put aside and dug once afterwards. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void summonCommandParksTheDigAndTheRestIsDugOnce(GameTestHelper helper) {
        Scene scene = digScene(helper, false);
        Watch watch = new Watch();
        watchOrder(helper, scene, watch, true, () -> command(scene.player(), "companion summon"));
        finishWhen(helper, scene, watch, true);
    }

    /** With the setting off an urgent order stops the dig as it did before: nothing is parked, nothing comes back. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void switchedOffUrgentOrderParksNothing(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setUrgentOrder(false);
        vein(helper);
        ServerPlayer player = owner(helper, companion);
        long[] orderedAt = {-1};
        int[] veinAtOrder = {-1};
        helper.onEachTick(() -> {
            if (orderedAt[0] < 0) {
                if (count(companion, Items.COBBLESTONE) < 2) return;
                orderedAt[0] = helper.getTick();
                helper.assertTrue(!companion.parkWork(), "The dig was put aside with the setting off");
                CompanionEvents.executeAction(player, "come", "", 1);
                return;
            }
            CompoundTag data = new CompoundTag();
            companion.saveWithoutId(data);
            helper.assertTrue(!data.contains("ParkedAction"), "Something was parked with the setting off");
            if (helper.getTick() - orderedAt[0] == 2) veinAtOrder[0] = veinLeft(helper);
        });
        helper.assertTrue(companion.startWork("gather_stone", "", WANTED).success(), "Stone work was not accepted");
        helper.succeedWhen(() -> {
            helper.assertTrue(orderedAt[0] >= 0 && helper.getTick() - orderedAt[0] >= PLAIN_WATCH_TICKS, "Watching the order");
            helper.assertTrue(!companion.isWorking() && veinLeft(helper) == veinAtOrder[0], "He went back to the dig with the setting off");
        });
    }

    /** The setting is kept with him; a save from before it existed has it on. */
    @GameTest(template = "tall_tree")
    public static void urgentOrderSettingIsSavedAndAnOldSaveHasItOn(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        helper.assertTrue(companion.urgentOrder(), "The setting is not on by default");
        companion.setUrgentOrder(false);
        CompoundTag data = new CompoundTag();
        companion.saveWithoutId(data);
        CompanionEntity other = AICompanionMod.COMPANION.get().create(helper.getLevel());
        other.load(data);
        helper.assertTrue(!other.urgentOrder(), "The switch was not saved");
        data.remove("UrgentOrder");
        other.setUrgentOrder(false);
        other.load(data);
        helper.assertTrue(other.urgentOrder(), "An old save loaded with the setting off");
        helper.succeed();
    }

    private static void comeUrgently(Scene scene) {
        scene.link().cancelAll(false, false, true);
        scene.link().submit(List.of(new PlannedAction("come", 0, 1)), "urgent-come", () -> false);
    }

    /** Two natural oaks beside the walk to the player: leaves that rot away are not his to break, the trunks are. */
    private static void trees(GameTestHelper helper) {
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int z : new int[]{12, 24}) {
            for (int y = 1; y <= TREE_HEIGHT; y++) helper.setBlock(new BlockPos(22, y, z), Blocks.OAK_LOG);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = TREE_HEIGHT - 1; y <= TREE_HEIGHT + 1; y++) {
                BlockPos pos = new BlockPos(22 + dx, y, z + dz);
                if (helper.getBlockState(pos).isAir()) helper.setBlock(pos, leaves);
            }
        }
    }

    private static int logBlocks(GameTestHelper helper) {
        int logs = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 32, 31))) {
            if (helper.getBlockState(pos).is(BlockTags.LOGS)) logs++;
        }
        return logs;
    }

    private static int logsIn(CompanionEntity companion) {
        int logs = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(ItemTags.LOGS)) logs += companion.getItem(slot).getCount();
        return logs;
    }

    private static void command(ServerPlayer player, String line) {
        player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), line);
    }

    private static void queuedDigThenCancel(GameTestHelper helper, boolean urgent, boolean goalPaused) {
        Scene scene = digScene(helper, goalPaused);
        Watch watch = new Watch();
        watchOrder(helper, scene, watch, urgent, () -> {
            scene.link().cancelAll(false, false, urgent);
            if (urgent) scene.link().submit(List.of(new PlannedAction("come", 0, 1)), "urgent-come", () -> false);
        });
        finishWhen(helper, scene, watch, urgent);
    }

    /** Dig started through the queue (so it is a player order), with a pickaxe goal outstanding when asked. */
    private static Scene digScene(GameTestHelper helper, boolean goalPaused) {
        CompanionEntity companion = setup(helper);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        vein(helper);
        ServerPlayer player = owner(helper, companion);
        if (goalPaused) pauseGoal(helper, companion, player);
        CompanionParkLink link = new CompanionParkLink();
        link.bind(helper.getLevel().getServer(), player.getUUID());
        link.submit(List.of(new PlannedAction("gather_stone", 0, WANTED)), "urgent-queue-test", () -> false);
        return new Scene(companion, player, link);
    }

    private static void pauseGoal(GameTestHelper helper, CompanionEntity companion, ServerPlayer player) {
        var accepted = CompanionEvents.executeAction(player, "goal_pickaxes", "", 1);
        helper.assertTrue(accepted.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + accepted.message());
        helper.assertTrue(CompanionEvents.executeAction(player, "wait", "", 1).success(), "The wait order failed");
        companion.setFollowing(true);
        helper.assertTrue(companion.goals().state() == CompanionGoals.State.PAUSED_PLAYER, "Goal not paused: " + companion.goals().state());
    }

    /** Runs {@code order} once two stones are dug, then checks the park (urgent) or its absence (plain) on every tick. */
    private static void watchOrder(GameTestHelper helper, Scene scene, Watch watch, boolean urgent, Runnable order) {
        CompanionEntity companion = scene.companion();
        helper.onEachTick(() -> {
            if (watch.orderedAt < 0) {
                if (count(companion, Items.COBBLESTONE) < 2) return;
                watch.orderedAt = helper.getTick();
                order.run();
                return;
            }
            long since = helper.getTick() - watch.orderedAt;
            CompoundTag data = new CompoundTag();
            companion.saveWithoutId(data);
            if (since == 1) watch.veinAtOrder = veinLeft(helper);
            if (!urgent) {
                helper.assertTrue(!data.contains("ParkedAction"), "A plain cancel left a park");
                helper.assertTrue(!companion.isWorking() && veinLeft(helper) == watch.veinAtOrder, "He dug right after the plain cancel");
                return;
            }
            if (!watch.arrived) {
                helper.assertTrue("gather_stone".equals(data.getString("ParkedAction")),
                        "Park eaten by the order: tag='" + data.getString("ParkedAction") + "', since=" + since);
                helper.assertTrue(data.getInt("ParkedCount") + count(companion, Items.COBBLESTONE) == WANTED,
                        "The rest and the bag do not add up to the order: " + data.getInt("ParkedCount") + " + " + count(companion, Items.COBBLESTONE));
                helper.assertTrue(veinLeft(helper) == watch.veinAtOrder, "He dug before he came: " + watch.veinAtOrder + " -> " + veinLeft(helper));
            }
            if (companion.distanceTo(scene.player()) <= ARRIVED) watch.arrived = true;
        });
    }

    private static void finishWhen(GameTestHelper helper, Scene scene, Watch watch, boolean urgent) {
        CompanionEntity companion = scene.companion();
        helper.succeedWhen(() -> {
            helper.assertTrue(watch.orderedAt >= 0, "The dig has not gone far enough to be ordered away yet");
            if (!urgent) {
                helper.assertTrue(helper.getTick() - watch.orderedAt >= PLAIN_WATCH_TICKS, "Watching the plain cancel");
                return;
            }
            helper.assertTrue(watch.arrived, "He has not come to the player yet");
            done(helper, companion);
            helper.assertTrue(count(companion, Items.COBBLESTONE) == WANTED, "The rest was not dug once: " + count(companion, Items.COBBLESTONE));
            helper.assertTrue(veinLeft(helper) == EXTRA, "Over-dug or under-dug: " + veinLeft(helper) + " blocks left, expected " + EXTRA);
        });
    }

    private record Scene(CompanionEntity companion, ServerPlayer player, CompanionParkLink link) {}

    private static final class Watch {
        long orderedAt = -1;
        int veinAtOrder;
        boolean arrived;
    }

    private static CompanionEntity setup(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var owner = helper.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }

    private static void vein(GameTestHelper helper) {
        for (int z = 16; z < 16 + WANTED + EXTRA; z++) helper.setBlock(new BlockPos(20, 1, z), Blocks.STONE);
    }

    private static int veinLeft(GameTestHelper helper) {
        int left = 0;
        for (int z = 16; z < 16 + WANTED + EXTRA; z++) if (helper.getBlockState(new BlockPos(20, 1, z)).is(Blocks.STONE)) left++;
        return left;
    }

    private static int count(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) count += container.getItem(slot).getCount();
        }
        if (container instanceof CompanionEntity companion) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
                if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
            }
        }
        return count;
    }

    private static void done(GameTestHelper helper, CompanionEntity companion) {
        helper.assertTrue(!companion.isGatheringTask() && !companion.isWorking(),
                "Work still active: " + companion.getLastTaskResult() + " at " + companion.position());
    }

    private static ServerPlayer owner(GameTestHelper helper, CompanionEntity companion) {
        return helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
}

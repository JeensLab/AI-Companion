package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.*;
import dev.aicompanion.entity.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionTaskTests {
    private static List<PlannedAction> action(String item) { return List.of(new PlannedAction("craft_item", 0, 1, item)); }
    private static CompanionTaskQueue.Result ok() { return new CompanionTaskQueue.Result("Готово.", true); }
    @GameTest(template="empty", timeoutTicks=100)
    public static void prioritiesReorderRequestsAndKeepWholeChains(GameTestHelper h) {
        var q = new CompanionTaskQueue(); var gate = new CompletableFuture<CompanionTaskQueue.Result>();
        List<String> order = new ArrayList<>();
        q.submit(action("active"), t -> { order.add("active"); return gate; });
        var chain = List.of(new PlannedAction("gather_wood",0,8),new PlannedAction("craft_item",0,1,"minecraft:chest"));
        q.submit(chain, t -> { order.add("chain"); t.progress(chain,1); return CompletableFuture.completedFuture(ok()); });
        q.submit(action("high"), t -> { order.add("high"); return CompletableFuture.completedFuture(ok()); });
        q.submit(action("low"), t -> { order.add("low"); return CompletableFuture.completedFuture(ok()); });
        var before = q.snapshot(); q.priority(before.get(2).id(),2); q.priority(before.get(3).id(),0);
        h.assertTrue(order.equals(List.of("active")), "Priority preempted active physical work");
        h.assertTrue(q.snapshot().get(1).steps().equals(action("high")), "UI and execution orders differ");
        gate.complete(ok());
        h.assertTrue(order.equals(List.of("active","high","chain","low")), "Wrong actual dispatch order: "+order);
        var saved = q.snapshot().stream().filter(v -> v.steps().equals(chain)).findFirst().orElseThrow();
        h.assertTrue(saved.step()==1 && saved.status()==CompanionTaskQueue.Status.COMPLETED, "Lost chain progress");
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void cancellingOneRequestKeepsTheRestAndWaitsForPhysicalStop(GameTestHelper h) {
        var q = new CompanionTaskQueue(); var gate = new CompletableFuture<CompanionTaskQueue.Result>();
        var stopped = new AtomicBoolean(); List<String> order = new ArrayList<>();
        q.submit(action("active"), t -> { t.onCancel(() -> stopped.set(true)); return gate; });
        var removed=q.submit(action("removed"), t -> { order.add("removed"); return CompletableFuture.completedFuture(ok()); });
        q.submit(action("kept"), t -> { order.add("kept"); return CompletableFuture.completedFuture(ok()); });
        long id=q.snapshot().getFirst().id();
        q.cancel(q.snapshot().get(1).id()); q.cancel(id);
        h.assertTrue(stopped.get() && order.isEmpty() && !removed.completion().isCompletedExceptionally(), "Cancellation overlapped work");
        h.assertTrue(!q.contains(action("active")), "Cancelled job still suppresses a new request");
        gate.complete(ok());
        h.assertTrue(order.equals(List.of("kept")), "Cancelled another task or started removed request: "+order);
        h.assertTrue(q.snapshot().stream().filter(v -> v.status()==CompanionTaskQueue.Status.CANCELLED).count()==2,"Lost cancellation history");
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void worldResetDropsOldTasksAndHistoryEvenIfTheyFinishLater(GameTestHelper h) {
        var q = new CompanionTaskQueue(); var gate = new CompletableFuture<CompanionTaskQueue.Result>();
        q.submit(action("old"), t -> gate); q.submit(action("waiting"), t -> CompletableFuture.completedFuture(ok()));
        q.cancelAll(true); h.assertTrue(q.snapshot().isEmpty(), "Old world still visible");
        gate.complete(ok()); h.assertTrue(q.snapshot().isEmpty(), "Late completion leaked into new world");
        q.submit(action("new"), t -> CompletableFuture.completedFuture(ok()));
        h.assertTrue(q.snapshot().size()==1 && q.snapshot().getFirst().status()==CompanionTaskQueue.Status.COMPLETED,"New world is stuck");
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void failedRequestDoesNotBlockNextAndEqualPrioritiesAreStable(GameTestHelper h) {
        var q = new CompanionTaskQueue(); var gate = new CompletableFuture<CompanionTaskQueue.Result>();
        List<String> order = new ArrayList<>();
        q.submit(action("failure"), t -> gate);
        q.submit(action("first"), t -> {order.add("first");return CompletableFuture.completedFuture(ok());});
        q.submit(action("second"), t -> {order.add("second");return CompletableFuture.completedFuture(ok());});
        gate.completeExceptionally(new IllegalStateException("Нет материалов"));
        h.assertTrue(order.equals(List.of("first","second")), "Failure wedged queue or changed FIFO");
        h.assertTrue(q.snapshot().getLast().status()==CompanionTaskQueue.Status.BLOCKED,"Failure shown as completion");
        h.succeed();
    }
    @GameTest(template="tall_tree", timeoutTicks=100)
    public static void boardIsLiveAndRejectsStaleOrForeignCancellation(GameTestHelper h) {
        var c=h.spawn(AICompanionMod.COMPANION.get(),16,1,16);var p=h.makeMockServerPlayerInLevel();
        p.teleportTo(c.getX()-3,c.getY(),c.getZ()-3);c.setOwnerId(p.getUUID()); c.setLifestyle(false,false); c.setSearchRadius(16);
        p.getPersistentData().putUUID("aicompanion:canonical_companion",c.getUUID());CompanionEvents.getOwnedCompanion(p);
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)h.setBlock(new net.minecraft.core.BlockPos(x,0,z),net.minecraft.world.level.block.Blocks.BEDROCK);
        var initial=CompanionTaskBoard.read(p);
        h.assertTrue(initial!=null && initial.work()==0,"Board cannot find owner");
        c.startWork("gather_stone","",8);long work=c.activeWorkId();
        CompanionTaskBoard.control(p,initial,"cancel_work");
        h.assertTrue(c.activeWorkId()==work,"Stale button cancelled new work");
        var current=CompanionTaskBoard.read(p); h.assertTrue(current.work()==work && current.activity().contains("добычу камня"),"Board missed live work");
        var other=h.makeMockServerPlayerInLevel();
        CompanionTaskBoard.control(other,current,"cancel_work");h.assertTrue(c.activeWorkId()==work,"Foreign player cancelled work");
        CompanionTaskBoard.control(p,current,"cancel_work");h.assertTrue(c.activeWorkId()==0,"Physical work continued after cancel");
        h.succeed();
    }
    @GameTest(template="tall_tree", timeoutTicks=100)
    public static void cancellingSavedGoalDoesNotCancelUnrelatedWork(GameTestHelper h) {
        var c=h.spawn(AICompanionMod.COMPANION.get(),16,1,16);var p=h.makeMockServerPlayerInLevel();
        p.teleportTo(c.getX()-3,c.getY(),c.getZ()-3);c.setOwnerId(p.getUUID()); c.setLifestyle(false,false); c.setSearchRadius(16);
        p.getPersistentData().putUUID("aicompanion:canonical_companion",c.getUUID());CompanionEvents.getOwnedCompanion(p);
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)h.setBlock(new net.minecraft.core.BlockPos(x,0,z),net.minecraft.world.level.block.Blocks.BEDROCK);
        c.goals().accept(p); CompanionEvents.executeAction(p,"gather_stone","",8);
        long work=c.activeWorkId();var shown=CompanionTaskBoard.read(p);
        h.assertTrue(work>0 && !shown.goalNext().isEmpty(),"Goal or alternate work missing");
        CompanionTaskBoard.control(p,shown,"cancel_goal");
        h.assertTrue(c.goals().state()==CompanionGoals.State.CANCELLED && c.activeWorkId()==work,"Goal cancellation damaged another request");
        net.minecraft.nbt.CompoundTag saved=new net.minecraft.nbt.CompoundTag();c.goals().save(saved);c.goals().load(saved);
        h.assertTrue(c.goals().state()==CompanionGoals.State.CANCELLED,"Cancelled goal resurrected on reload");
        h.succeed();
    }
    @GameTest(template="tall_tree", timeoutTicks=140)
    public static void portCancellationStopsItsPhysicalWorkBeforeNextRequest(GameTestHelper h) {
        var c=h.spawn(AICompanionMod.COMPANION.get(),16,1,16);var p=h.makeMockServerPlayerInLevel();
        p.teleportTo(c.getX()-3,c.getY(),c.getZ()-3);c.setOwnerId(p.getUUID()); c.setLifestyle(false,false); c.setSearchRadius(16);
        p.getPersistentData().putUUID("aicompanion:canonical_companion",c.getUUID());CompanionEvents.getOwnedCompanion(p);
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)h.setBlock(new net.minecraft.core.BlockPos(x,0,z),net.minecraft.world.level.block.Blocks.BEDROCK);
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_AXE));
        h.setBlock(new net.minecraft.core.BlockPos(20,1,16),net.minecraft.world.level.block.Blocks.OAK_LOG);
        var cancelled=new AtomicBoolean();
        var port=new CompanionServerPlanPort(h.getLevel().getServer(),p.getUUID(),"task-cancel-test",cancelled::get);
        var run=port.run(new PlannedAction("gather_wood",0,64));
        h.runAfterDelay(3,()->{
            h.assertTrue(c.activeWorkId()>0,"Physical task did not start");
            cancelled.set(true);port.cancelWork();
        });
        h.succeedWhen(()->{
            h.assertTrue(run.isDone() && !run.isCompletedExceptionally(),"Cancellation did not settle");
            h.assertTrue(c.activeWorkId()==0 && !run.join().result().success(),"Cancelled physical work survived");
            var next=c.startWork("gather_stone","",8);
            h.assertTrue(next.success() && c.activeWorkId()>0,"Next request blocked by cancelled task");
        });
    }
    private static CompanionTaskQueue queue(List<String> events, java.util.function.Predicate<List<PlannedAction>> held) {
        return new CompanionTaskQueue(held, new CompanionTaskQueue.BatchOwner() {
            @Override public String release(long batchId) { events.add("release:" + batchId); return "Мясо осталось в печи у 1 2 3."; }
            @Override public void orphan(long batchId) { events.add("orphan:" + batchId); }
        });
    }
    /** A task that parks on a batch and, once resumed, lets the test decide how it ends through {@code end}. */
    private static CompanionTaskQueue.Task parkedTask(CompanionTaskQueue q, String name, long batch, AtomicBoolean ready, boolean collects, CompletableFuture<CompanionTaskQueue.Result> end) {
        return q.submit(action(name), t -> {
            t.await(batch, ready::get).whenComplete((none, error) -> {
                if (error != null) { end.completeExceptionally(error); return; }
                if (collects) t.collected(batch);
            });
            return end;
        });
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void waitingTaskGivesUpTheSlotUntilReady(GameTestHelper h) {
        var q = new CompanionTaskQueue(); var ready = new AtomicBoolean(); var end = new CompletableFuture<CompanionTaskQueue.Result>();
        List<String> order = new ArrayList<>();
        var smelter = q.submit(action("smelter"), t -> {
            order.add("smelter");
            t.await(7, ready::get).thenRun(() -> { order.add("resumed"); end.complete(ok()); });
            return end;
        });
        q.submit(action("other"), t -> { order.add("other"); return CompletableFuture.completedFuture(ok()); });
        h.assertTrue(order.equals(List.of("smelter", "other")), "A waiting task kept the slot: " + order);
        h.assertTrue(q.snapshot().stream().anyMatch(v -> v.steps().equals(action("smelter")) && v.status() == CompanionTaskQueue.Status.WAITING), "Waiting task is not shown as WAITING");
        q.tick(); h.assertTrue(!order.contains("resumed"), "Resumed before the batch was ready");
        ready.set(true); h.assertTrue(!order.contains("resumed"), "Readiness alone resumed the task outside tick");
        q.tick(); h.assertTrue(order.equals(List.of("smelter", "other", "resumed")), "Ready task did not resume: " + order);
        h.assertTrue(smelter.completion().isDone() && q.snapshot().stream().anyMatch(v -> v.steps().equals(action("smelter")) && v.status() == CompanionTaskQueue.Status.COMPLETED), "Resumed task did not finish");
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void readyWaitingTaskGoesBeforeQueuedOnes(GameTestHelper h) {
        var q = new CompanionTaskQueue(); var ready = new AtomicBoolean(); var end = new CompletableFuture<CompanionTaskQueue.Result>();
        var gate = new CompletableFuture<CompanionTaskQueue.Result>(); List<String> order = new ArrayList<>();
        q.submit(action("smelter"), t -> {
            t.await(7, ready::get).thenRun(() -> { order.add("resumed"); end.complete(ok()); });
            return end;
        });
        q.submit(action("blocker"), t -> { order.add("blocker"); return gate; });
        q.submit(action("later"), t -> { order.add("later"); return CompletableFuture.completedFuture(ok()); });
        q.priority(q.snapshot().stream().filter(v -> v.steps().equals(action("later"))).findFirst().orElseThrow().id(), 2);
        ready.set(true); q.tick();
        h.assertTrue(order.equals(List.of("blocker")), "Tick took the slot from running work: " + order);
        gate.complete(ok());
        h.assertTrue(order.equals(List.of("blocker", "resumed", "later")), "Queued task jumped a ready waiting one: " + order);
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void requestNeedingTheProductIsHeldUntilCollected(GameTestHelper h) {
        var parked = new ArrayList<CompanionParkHold.Parked>(); var ready = new AtomicBoolean();
        var q = queue(new ArrayList<>(), steps -> CompanionParkHold.held(steps, parked));
        var end = new CompletableFuture<CompanionTaskQueue.Result>(); List<String> order = new ArrayList<>();
        parked.add(new CompanionParkHold.Parked("minecraft:beef", "minecraft:cooked_beef"));
        q.submit(action("smelter"), t -> {
            t.await(7, ready::get).thenRun(() -> { t.collected(7); parked.clear(); order.add("collected"); end.complete(ok()); });
            return end;
        });
        q.submit(List.of(new PlannedAction("give_items", 0, 2, "minecraft:cooked_beef")), t -> { order.add("give"); return CompletableFuture.completedFuture(ok()); });
        q.submit(List.of(new PlannedAction("smelt_items", 0, 2, "minecraft:beef")), t -> { order.add("smelt"); return CompletableFuture.completedFuture(ok()); });
        q.submit(List.of(new PlannedAction("collect_smelt", 0, 1, "7")), t -> { order.add("collect"); return CompletableFuture.completedFuture(ok()); });
        q.submit(action("minecraft:chest"), t -> { order.add("free"); return CompletableFuture.completedFuture(ok()); });
        h.assertTrue(order.equals(List.of("collect", "free")), "Held requests were not skipped, or a free one was blocked: " + order);
        h.assertTrue(q.snapshot().stream().filter(v -> v.status() == CompanionTaskQueue.Status.QUEUED).count() == 2, "Held requests left the queue");
        ready.set(true); q.tick();
        h.assertTrue(order.equals(List.of("collect", "free", "collected", "give", "smelt")), "Held requests did not resume in order: " + order);
        var meat = new CompanionParkHold.Parked("minecraft:beef", "minecraft:cooked_beef");
        h.assertTrue(CompanionParkHold.held(List.of(new PlannedAction("hunt_animals", 0, 2, "minecraft:cow")), List.of(meat)), "Hunting the input is not held");
        h.assertTrue(!CompanionParkHold.held(List.of(new PlannedAction("hunt_animals", 0, 2, "minecraft:pig")), List.of(meat)), "Hunting another animal is held");
        h.assertTrue(!CompanionParkHold.held(List.of(new PlannedAction("gather_wood", 0, 8)), List.of(meat)), "Wood is held for meat");
        h.assertTrue(!CompanionParkHold.held(List.of(new PlannedAction("smelt_items", 0, 2, "minecraft:porkchop")), List.of(meat)), "Another input is held");
        h.assertTrue(!CompanionParkHold.held(List.of(new PlannedAction("give_items", 0, 2, "minecraft:cobblestone")), List.of(meat)), "Another item is held");
        h.assertTrue(!CompanionParkHold.held(List.of(new PlannedAction("give_items", 0, 2, "minecraft:cooked_beef")), List.of()), "Held with nothing parked");
        h.assertTrue(!CompanionParkHold.held(List.of(new PlannedAction("collect_smelt", 0, 1, "7")), List.of(meat)), "A collection is held on its own batch");
        h.assertTrue(CompanionParkHold.held(List.of(new PlannedAction("frobnicate", 0, 1)), List.of(meat)), "An unknown step type is not held");
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void everyEndOfAParkedTaskDisposesItsBatch(GameTestHelper h) {
        List<String> events = new ArrayList<>(); java.util.function.Predicate<List<PlannedAction>> none = steps -> false;
        var ready = new AtomicBoolean(true);
        var q = queue(events, none); var end = new CompletableFuture<CompanionTaskQueue.Result>();
        parkedTask(q, "collected", 1, ready, true, end); q.tick(); end.complete(ok());
        h.assertTrue(events.isEmpty(), "A batch its task collected was disposed: " + events);
        q = queue(events, none); end = new CompletableFuture<>();
        parkedTask(q, "completed", 2, ready, false, end); q.tick(); end.complete(ok());
        h.assertTrue(events.equals(List.of("orphan:2")), "Completed task left its batch: " + events); events.clear();
        q = queue(events, none); end = new CompletableFuture<>();
        parkedTask(q, "blocked", 3, ready, false, end); q.tick(); end.complete(new CompanionTaskQueue.Result("Нет угля.", false));
        h.assertTrue(events.equals(List.of("orphan:3")), "Blocked task left its batch: " + events); events.clear();
        q = queue(events, none); end = new CompletableFuture<>();
        parkedTask(q, "failed", 4, ready, false, end); q.tick(); end.completeExceptionally(new IllegalStateException("порт"));
        h.assertTrue(events.equals(List.of("orphan:4")), "Failed task left its batch: " + events); events.clear();
        var notReady = new AtomicBoolean();
        q = queue(events, none); end = new CompletableFuture<>();
        var waiter = parkedTask(q, "cancelled while waiting", 5, notReady, false, end);
        q.cancel(q.snapshot().getFirst().id());
        h.assertTrue(events.equals(List.of("release:5")), "Player cancel did not release the batch: " + events); events.clear();
        h.assertTrue(q.snapshot().getFirst().report().contains("Мясо осталось в печи у 1 2 3."), "Release note missing from the report: " + q.snapshot().getFirst().report());
        h.assertTrue(waiter.completion().isDone() && end.isCompletedExceptionally(), "Cancelled waiting task still hangs");
        q = queue(events, none); end = new CompletableFuture<>();
        var runner = parkedTask(q, "cancelled running", 6, ready, false, end); q.tick();
        q.cancel(q.snapshot().getFirst().id()); end.complete(ok());
        h.assertTrue(events.equals(List.of("release:6")) && runner.completion().join().contains("Мясо осталось"), "Cancel of a resumed task: " + events); events.clear();
        q = queue(events, none); var waitingEnd = new CompletableFuture<CompanionTaskQueue.Result>(); end = new CompletableFuture<>();
        parkedTask(q, "world waiting", 7, notReady, false, waitingEnd);
        var worldReady = new AtomicBoolean(); parkedTask(q, "world running", 8, worldReady, false, end);
        worldReady.set(true); q.tick(); q.cancelAll(false); end.complete(ok());
        h.assertTrue(events.size() == 2 && events.containsAll(List.of("orphan:7", "orphan:8")), "World change did not orphan both batches: " + events);
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void duplicateOfAWaitingTaskIsRejected(GameTestHelper h) {
        var q = new CompanionTaskQueue(); var end = new CompletableFuture<CompanionTaskQueue.Result>();
        q.submit(action("smelter"), t -> { t.await(7, () -> false); return end; });
        h.assertTrue(q.contains(action("smelter")), "A waiting task can be queued twice");
        h.assertTrue(q.priority(q.snapshot().getFirst().id(), 2) && q.snapshot().getFirst().priority() == 2, "Waiting task ignores priority");
        q.cancel(q.snapshot().getFirst().id());
        h.assertTrue(!q.contains(action("smelter")), "Cancelled waiting task still suppresses a new request");
        h.succeed();
    }
    /** A task that gave its slot to a cooked batch gets it back right after the collector, before a queued task; the collector is "borrowed" meanwhile. */
    @GameTest(template="empty", timeoutTicks=100)
    public static void yieldedTaskGetsTheSlotBackAfterTheCollect(GameTestHelper h) {
        var q = new CompanionTaskQueue(); var ready = new AtomicBoolean(); List<String> order = new ArrayList<>();
        var collectorEnd = new CompletableFuture<CompanionTaskQueue.Result>(); var walkerEnd = new CompletableFuture<CompanionTaskQueue.Result>();
        q.submit(action("smelter"), t -> {
            order.add("smelter");
            t.await(7, ready::get).thenRun(() -> order.add("collect:borrowed=" + t.borrowed()));
            return collectorEnd;
        });
        var walker = q.submit(action("walker"), t -> { order.add("walker"); return walkerEnd; });
        q.submit(action("queued"), t -> { order.add("queued"); return CompletableFuture.completedFuture(ok()); });
        h.assertTrue(walker.readyBatches().isEmpty() && !walker.borrowed(), "Nothing is ready yet, nothing is borrowed");
        ready.set(true); q.tick();
        h.assertTrue(order.equals(List.of("smelter", "walker")) && walker.readyBatches().equals(List.of(7L)), "Tick took the slot or lost the ready batch: " + order);
        walker.yieldSlot(java.util.Set.of(7L)).thenRun(() -> { order.add("walker:borrowed=" + walker.borrowed()); walkerEnd.complete(ok()); });
        h.assertTrue(order.equals(List.of("smelter", "walker", "collect:borrowed=true")), "The cooked batch did not get the slot: " + order);
        h.assertTrue(q.snapshot().stream().anyMatch(v -> v.steps().equals(action("walker")) && v.status() == CompanionTaskQueue.Status.WAITING), "The yielded task is not shown as waiting");
        collectorEnd.complete(ok());
        h.assertTrue(order.equals(List.of("smelter", "walker", "collect:borrowed=true", "walker:borrowed=false", "queued")), "The slot did not go back to the yielded task before the queued one: " + order);
        h.succeed();
    }
    /** The collector that is borrowing the slot gives it back at its own boundary; the lender is not asked to give it away again. */
    @GameTest(template="empty", timeoutTicks=100)
    public static void borrowingCollectorYieldsBackAtItsBoundary(GameTestHelper h) {
        var q = new CompanionTaskQueue(); var ready = new AtomicBoolean(); List<String> order = new ArrayList<>();
        var collectorEnd = new CompletableFuture<CompanionTaskQueue.Result>(); var walkerEnd = new CompletableFuture<CompanionTaskQueue.Result>();
        var collector = new CompanionTaskQueue.Task[1];
        q.submit(action("smelter"), t -> {
            collector[0] = t;
            t.await(7, ready::get).thenRun(() -> order.add("collect:borrowed=" + t.borrowed()));
            return collectorEnd;
        });
        var walker = q.submit(action("walker"), t -> { order.add("walker"); return walkerEnd; });
        ready.set(true); q.tick();
        walker.yieldSlot(java.util.Set.of(7L)).thenRun(() -> order.add("walker-back:borrowed=" + walker.borrowed()));
        collector[0].yieldSlot(java.util.Set.of()).thenRun(() -> order.add("collector-back"));
        h.assertTrue(order.equals(List.of("walker", "collect:borrowed=true", "walker-back:borrowed=false")), "The lender did not get the slot back first: " + order);
        walkerEnd.complete(ok());
        h.assertTrue(order.equals(List.of("walker", "collect:borrowed=true", "walker-back:borrowed=false", "collector-back")), "The collector did not get on after the lender: " + order);
        collectorEnd.complete(ok());
        h.succeed();
    }
    /** Only the batch the giver named (the near one) takes the slot; a far cooked batch, even one that was ready earlier, waits behind the giver. */
    @GameTest(template="empty", timeoutTicks=100)
    public static void farReadyBatchDoesNotOvertakeTheYieldedTask(GameTestHelper h) {
        var q = new CompanionTaskQueue(); var ready = new AtomicBoolean(); List<String> order = new ArrayList<>();
        var farEnd = new CompletableFuture<CompanionTaskQueue.Result>(); var nearEnd = new CompletableFuture<CompanionTaskQueue.Result>(); var walkerEnd = new CompletableFuture<CompanionTaskQueue.Result>();
        q.submit(action("far"), t -> { t.await(8, ready::get).thenRun(() -> order.add("far")); return farEnd; });
        q.submit(action("near"), t -> { t.await(7, ready::get).thenRun(() -> order.add("near")); return nearEnd; });
        var walker = q.submit(action("walker"), t -> { order.add("walker"); return walkerEnd; });
        ready.set(true); q.tick();
        h.assertTrue(order.equals(List.of("walker")) && walker.readyBatches().containsAll(List.of(7L, 8L)), "Setup: " + order + " " + walker.readyBatches());
        walker.yieldSlot(java.util.Set.of(7L)).thenRun(() -> order.add("walker-back"));
        h.assertTrue(order.equals(List.of("walker", "near")), "The far batch was collected before the near one: " + order);
        nearEnd.complete(ok());
        h.assertTrue(order.equals(List.of("walker", "near", "walker-back")), "A far ready batch overtook the yielded task: " + order);
        walkerEnd.complete(ok());
        h.assertTrue(order.equals(List.of("walker", "near", "walker-back", "far")), "The far batch did not follow the end of the task: " + order);
        farEnd.complete(ok());
        h.succeed();
    }
    /** A collector that fails still gives the slot back to the task that lent it, before a queued one. */
    @GameTest(template="empty", timeoutTicks=100)
    public static void collectorFailureGivesTheSlotBack(GameTestHelper h) {
        var q = new CompanionTaskQueue(); var ready = new AtomicBoolean(); List<String> order = new ArrayList<>();
        var collectorEnd = new CompletableFuture<CompanionTaskQueue.Result>(); var walkerEnd = new CompletableFuture<CompanionTaskQueue.Result>();
        q.submit(action("smelter"), t -> { t.await(7, ready::get).thenRun(() -> order.add("collect")); return collectorEnd; });
        var walker = q.submit(action("walker"), t -> { order.add("walker"); return walkerEnd; });
        q.submit(action("queued"), t -> { order.add("queued"); return CompletableFuture.completedFuture(ok()); });
        ready.set(true); q.tick();
        walker.yieldSlot(java.util.Set.of(7L)).thenRun(() -> order.add("walker-back"));
        collectorEnd.completeExceptionally(new IllegalStateException("boom"));
        h.assertTrue(order.equals(List.of("walker", "collect", "walker-back")), "The failed collector did not give the slot back: " + order);
        h.assertTrue(q.snapshot().stream().anyMatch(v -> v.steps().equals(action("smelter")) && v.status() == CompanionTaskQueue.Status.BLOCKED), "The failed collector is not blocked");
        walkerEnd.complete(ok());
        h.assertTrue(order.equals(List.of("walker", "collect", "walker-back", "queued")), "The queue stopped after the failure: " + order);
        h.succeed();
    }
    /** The lender is cancelled while the collector runs on its slot: the collector is no longer a borrower and the slot goes on to the queue. */
    @GameTest(template="empty", timeoutTicks=100)
    public static void lenderCancelledWhileBorrowerCollects(GameTestHelper h) {
        var q = new CompanionTaskQueue(); var ready = new AtomicBoolean(); List<String> order = new ArrayList<>();
        var collectorEnd = new CompletableFuture<CompanionTaskQueue.Result>();
        var collector = new CompanionTaskQueue.Task[1];
        q.submit(action("smelter"), t -> { collector[0] = t; t.await(7, ready::get).thenRun(() -> order.add("collect:borrowed=" + t.borrowed())); return collectorEnd; });
        var walker = q.submit(action("walker"), t -> { order.add("walker"); return new CompletableFuture<>(); });
        q.submit(action("queued"), t -> { order.add("queued"); return CompletableFuture.completedFuture(ok()); });
        ready.set(true); q.tick();
        var yielded = walker.yieldSlot(java.util.Set.of(7L));
        h.assertTrue(collector[0].borrowed(), "The collector is not borrowing while the lender waits");
        long id = q.snapshot().stream().filter(v -> v.steps().equals(action("walker"))).findFirst().orElseThrow().id();
        h.assertTrue(q.cancel(id), "The cancel was refused");
        h.assertTrue(yielded.isCompletedExceptionally() && !collector[0].borrowed(), "The collector still thinks it borrows: " + order);
        collectorEnd.complete(ok());
        h.assertTrue(order.equals(List.of("walker", "collect:borrowed=true", "queued")), "The slot did not go on to the queue: " + order);
        h.succeed();
    }
    /** The first lender is cancelled while the collector on its slot waits behind another yielded task: it still gives the slot back at its boundary. */
    @GameTest(template="empty", timeoutTicks=100)
    public static void cancelledLenderStillReturnsTheSlotToAnotherYieldedTask(GameTestHelper h) {
        var q = new CompanionTaskQueue(); var ready = new AtomicBoolean(); List<String> order = new ArrayList<>();
        var firstEnd = new CompletableFuture<CompanionTaskQueue.Result>(); var secondEnd = new CompletableFuture<CompanionTaskQueue.Result>();
        var first = new CompanionTaskQueue.Task[1]; var second = new CompanionTaskQueue.Task[1];
        q.submit(action("first"), t -> { first[0] = t; t.await(7, ready::get).thenRun(() -> order.add("first")); return firstEnd; });
        q.submit(action("second"), t -> { second[0] = t; t.await(8, ready::get).thenRun(() -> order.add("second:borrowed=" + t.borrowed())); return secondEnd; });
        var walker = q.submit(action("walker"), t -> { order.add("walker"); return new CompletableFuture<>(); });
        ready.set(true); q.tick();
        walker.yieldSlot(java.util.Set.of(7L, 8L));
        first[0].yieldSlot(java.util.Set.of(8L)).thenRun(() -> order.add("first-back"));
        h.assertTrue(order.equals(List.of("walker", "first", "second:borrowed=true")), "Setup: " + order);
        long id = q.snapshot().stream().filter(v -> v.steps().equals(action("walker"))).findFirst().orElseThrow().id();
        h.assertTrue(q.cancel(id), "The cancel was refused");
        h.assertTrue(second[0].borrowed(), "The collector does not know that another task still waits for the slot");
        secondEnd.complete(ok());
        h.assertTrue(order.equals(List.of("walker", "first", "second:borrowed=true", "first-back")), "The slot did not go back to the waiting task: " + order);
        firstEnd.complete(ok());
        h.succeed();
    }
    /** The player cancels the task that gave its slot away: it ends cancelled, its batch is released, the slot goes on. */
    @GameTest(template="empty", timeoutTicks=100)
    public static void cancelledYieldedTaskReleasesItsBatch(GameTestHelper h) {
        List<String> events = new ArrayList<>(); var q = queue(events, steps -> false);
        var ready = new AtomicBoolean(); var collectorEnd = new CompletableFuture<CompanionTaskQueue.Result>();
        q.submit(action("smelter"), t -> { t.await(7, ready::get); return collectorEnd; });
        var failure = new CompletableFuture<Void>();
        var walker = q.submit(action("walker"), t -> { t.own(9); return new CompletableFuture<>(); });
        ready.set(true); q.tick();
        walker.yieldSlot(java.util.Set.of(7L)).whenComplete((none, error) -> failure.completeExceptionally(error));
        long id = q.snapshot().stream().filter(v -> v.steps().equals(action("walker"))).findFirst().orElseThrow().id();
        h.assertTrue(q.ownedBatches().contains(9L), "The yielded task's batch is not counted as owned");
        h.assertTrue(q.cancel(id), "The cancel was refused");
        h.assertTrue(failure.isCompletedExceptionally() && events.equals(List.of("release:9")), "Not cancelled and released: " + events);
        h.assertTrue(q.snapshot().stream().anyMatch(v -> v.steps().equals(action("walker")) && v.status() == CompanionTaskQueue.Status.CANCELLED), "Not shown as cancelled");
        collectorEnd.complete(ok());
        h.succeed();
    }
    private static PlannedAction step(String type, String item) { return new PlannedAction(type, 0, 1, item); }
    @GameTest(template="empty", timeoutTicks=100)
    public static void awaitAfterCancelStillDisposesTheBatch(GameTestHelper h) {
        List<String> events = new ArrayList<>(); var q = queue(events, steps -> false);
        var end = new CompletableFuture<CompanionTaskQueue.Result>();
        var cancelled = q.submit(action("parking"), t -> end);
        q.cancel(q.snapshot().getFirst().id());
        h.assertTrue(cancelled.await(5, () -> true).isCompletedExceptionally(), "Await on a cancelled task did not fail");
        end.complete(ok());
        h.assertTrue(events.equals(List.of("release:5")), "A batch parked after the cancel was lost: " + events); events.clear();
        var finished = q.submit(action("finished"), t -> CompletableFuture.completedFuture(ok()));
        h.assertTrue(finished.await(6, () -> true).isCompletedExceptionally(), "Await on a finished task did not fail");
        h.assertTrue(events.equals(List.of("orphan:6")), "A batch parked on a finished task was lost: " + events); events.clear();
        var waiter = parkedTask(q, "cancelled while waiting", 7, new AtomicBoolean(), false, new CompletableFuture<>());
        q.cancel(q.snapshot().getFirst().id()); events.clear();
        waiter.await(8, () -> true);
        h.assertTrue(events.equals(List.of("release:8")), "A batch parked after a player cancel of a waiting task was lost: " + events);
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void failingReadinessEndsTheTaskAndFreesTheSlot(GameTestHelper h) {
        List<String> events = new ArrayList<>(), order = new ArrayList<>(); var q = queue(events, steps -> false);
        var gate = new CompletableFuture<CompanionTaskQueue.Result>();
        var broken = q.submit(action("broken furnace"), t -> {
            t.await(1, () -> { throw new IllegalStateException("печь исчезла"); });
            return new CompletableFuture<>();
        });
        q.submit(action("blocker"), t -> { order.add("blocker"); return gate; });
        q.tick();
        h.assertTrue(q.snapshot().stream().anyMatch(v -> v.steps().equals(action("broken furnace")) && v.status() == CompanionTaskQueue.Status.BLOCKED), "A failing readiness did not end the task");
        h.assertTrue(broken.completion().isCompletedExceptionally() && events.equals(List.of("orphan:1")), "Failed task did not settle: " + events);
        gate.complete(ok());
        q.submit(action("next"), t -> { order.add("next"); return CompletableFuture.completedFuture(ok()); });
        h.assertTrue(order.equals(List.of("blocker", "next")), "A zombie task kept the slot: " + order);
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void cancellingAParkedTaskStartsTheReleasedHeldTask(GameTestHelper h) {
        var parked = new ArrayList<CompanionParkHold.Parked>(); List<String> order = new ArrayList<>();
        parked.add(new CompanionParkHold.Parked("minecraft:beef", "minecraft:cooked_beef"));
        var q = new CompanionTaskQueue(steps -> CompanionParkHold.held(steps, parked), new CompanionTaskQueue.BatchOwner() {
            @Override public String release(long batchId) { parked.clear(); return ""; }
            @Override public void orphan(long batchId) { parked.clear(); }
        });
        q.submit(action("smelter"), t -> { t.await(7, () -> false); return new CompletableFuture<>(); });
        q.submit(List.of(step("give_items", "minecraft:cooked_beef")), t -> { order.add("give"); return CompletableFuture.completedFuture(ok()); });
        h.assertTrue(order.isEmpty(), "A request needing the product ran before the cancel: " + order);
        q.cancel(q.snapshot().getFirst().id());
        h.assertTrue(order.equals(List.of("give")), "The released request waited for the next tick: " + order);
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void craftNeedingAParkedProductIsHeld(GameTestHelper h) {
        var iron = List.of(new CompanionParkHold.Parked("minecraft:raw_iron", "minecraft:iron_ingot"));
        var logs = List.of(new CompanionParkHold.Parked("minecraft:oak_log", "minecraft:charcoal"));
        var cobble = List.of(new CompanionParkHold.Parked("minecraft:cobblestone", "minecraft:stone"));
        var meat = List.of(new CompanionParkHold.Parked("minecraft:beef", "minecraft:cooked_beef"));
        java.util.function.Function<String, java.util.Set<String>> recipes = item -> switch (item) {
            case "iron_pickaxe" -> java.util.Set.of("iron_ingot", "stick");
            case "chest" -> java.util.Set.of("oak_planks");
            case "oak_planks" -> java.util.Set.of("oak_log");
            default -> java.util.Set.of();
        };
        var pickaxe = List.of(step("craft_item", "minecraft:iron_pickaxe"));
        h.assertTrue(CompanionParkHold.held(pickaxe, iron, recipes), "A craft needing the parked ingots is not held");
        h.assertTrue(CompanionParkHold.held(List.of(step("obtain_item", "minecraft:iron_pickaxe")), iron, recipes), "An obtain chain needing the parked ingots is not held");
        h.assertTrue(!CompanionParkHold.held(pickaxe, iron), "Without a recipe lookup a craft is held on a guess");
        h.assertTrue(!CompanionParkHold.held(pickaxe, meat, recipes), "A craft is held for an unrelated batch");
        h.assertTrue(CompanionParkHold.held(List.of(step("craft_item", "minecraft:chest")), logs, recipes), "A craft needing the parked item through a chain of recipes is not held");
        h.assertTrue(CompanionParkHold.held(List.of(step("place_block", "minecraft:stone")), cobble, recipes), "Placing the parked product is not held");
        h.assertTrue(!CompanionParkHold.held(List.of(step("place_block", "minecraft:dirt")), cobble, recipes), "Placing another block is held");
        h.assertTrue(CompanionParkHold.held(List.of(step("store_items", "stone")), cobble), "The stone alias is not matched");
        h.assertTrue(CompanionParkHold.held(List.of(step("give_items", "logs")), logs), "The logs alias is not matched");
        h.assertTrue(!CompanionParkHold.held(List.of(step("store_items", "logs")), cobble), "The logs alias is held for stone");
        h.assertTrue(CompanionParkHold.held(List.of(step("gather_wood", "")), logs), "Gathering the smelted logs is not held");
        h.assertTrue(CompanionParkHold.held(List.of(step("gather_stone", "")), cobble), "Gathering the smelted stone is not held");
        h.assertTrue(!CompanionParkHold.held(List.of(step("gather_wood", "")), meat), "Wood is held for meat");
        h.assertTrue(!CompanionParkHold.held(List.of(step("collect_smelt", "7")), iron, recipes), "A collection is held on a batch");
        h.succeed();
    }
}

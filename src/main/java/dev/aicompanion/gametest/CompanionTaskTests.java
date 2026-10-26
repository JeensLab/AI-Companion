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
}

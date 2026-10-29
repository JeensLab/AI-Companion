package dev.aicompanion.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.*;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionAgendaView;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.function.BiConsumer;

/** Opt-in UI integration and screenshot checks in a disposable offline world. Never shipped in the mod JAR. */
@EventBusSubscriber(modid=AICompanionMod.MOD_ID, value=Dist.CLIENT)
public final class CompanionTasksRenderProbe {
    private static boolean started;
    private static int phase, ticks;
    private static long chosen;
    private static volatile String serverFailure, seen = "";
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("aicompanion.tasksProbe")) return;
        var mc=Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started=true;
            mc.createWorldOpenFlows().createFreshLevel("OlegTasks-"+System.currentTimeMillis(),
                    new LevelSettings("Offline tasks QA",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(0L,false,false),registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
        }
        if (mc.level==null || mc.player==null) return;
        if (++ticks<40) return;
        ticks=0;
        check(serverFailure==null,"Server side of the probe failed: "+serverFailure);
        switch (phase++) {
            case 0 -> {
                mc.options.guiScale().set(3);mc.resizeDisplay();mc.setScreen(null);
                CompanionActionExecutor.execute(List.of(new PlannedAction("wait",600,1),new PlannedAction("craft_item",0,1,"minecraft:chest")));
                CompanionActionExecutor.execute(List.of(new PlannedAction("gather_stone",0,16),new PlannedAction("craft_item",0,1,"minecraft:stone_pickaxe")));
                CompanionActionExecutor.execute(List.of(new PlannedAction("craft_item",0,1,"minecraft:iron_axe")));
                CompanionActionExecutor.execute(List.of(new PlannedAction("build_house",0,1)));
                KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J));
            }
            case 1 -> {
                check(mc.screen instanceof CompanionTasksScreen,"J did not open task interface");
                var task=CompanionActionExecutor.tasks().stream().filter(t->t.status()==CompanionTaskQueue.Status.QUEUED).findFirst().orElseThrow();
                chosen=task.id();
                int panel=Math.min(740,mc.screen.width-24),left=(mc.screen.width-panel)/2;
                mc.screen.mouseClicked(left+40,135+36+10,0);
                press(mc,"Приоритет:");
                check(CompanionActionExecutor.tasks().stream().anyMatch(t->t.id()==chosen&&t.priority()==2),"Priority button did not change real queue");
            }
            case 2 -> shot(mc,"oleg-tasks-normal.png");
            case 3 -> { mc.options.guiScale().set(4);mc.resizeDisplay(); }
            case 4 -> shot(mc,"oleg-tasks-compact.png");
            case 5 -> {
                press(mc,"Отменить");
                check(CompanionActionExecutor.tasks().stream().anyMatch(t->t.id()==chosen&&t.status()==CompanionTaskQueue.Status.CANCELLED),"Cancel button did not cancel selected request");
                check(CompanionActionExecutor.tasks().stream().filter(t->t.status()==CompanionTaskQueue.Status.QUEUED).count()==2,"Cancel damaged other requests");
                press(mc,"Полный отчёт");
                press(mc,"Краткий итог");
                mc.options.guiScale().set(3);mc.resizeDisplay();press(mc,"Инвентарь");
            }
            case 6 -> {
                check(mc.screen instanceof CompanionInventoryScreen,"Inventory tab did not open");shot(mc,"oleg-tasks-inventory.png");
                press(mc,"Задачи");
            }
            case 7 -> {
                check(mc.screen instanceof CompanionTasksScreen,"Inventory task tab did not open");
                check(mc.player.containerMenu==mc.player.inventoryMenu,"Tasks left an inventory open");
                press(mc,"Чат");
            }
            case 8 -> { check(mc.screen instanceof CompanionChatScreen,"Chat tab did not open");shot(mc,"oleg-tasks-chat.png"); }
            case 9 -> {
                press(mc,"Задачи");
                check(mc.screen instanceof CompanionTasksScreen,"Chat tab did not return to tasks");
                CompanionActionExecutor.cancelPendingPlans();
                // Diamond gear is done, so the first milestone to work on is the food stock; "wait" holds the goal for the shots.
                onServer(mc,(player,oleg) -> {
                    oleg.clearContent();
                    oleg.setItem(0,new ItemStack(Items.DIAMOND_PICKAXE));oleg.setItem(1,new ItemStack(Items.DIAMOND_SWORD));
                    oleg.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));oleg.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
                    oleg.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));oleg.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));
                    check(CompanionAgenda.accept(player,oleg,"prepare_nether",0).status()==CompanionActionResult.Status.ACCEPTED,"Goal not accepted");
                    CompanionAgenda.playerOrder(player,oleg,"wait");
                });
            }
            case 10 -> {
                press(mc,"Цель:");
                check(enabled(mc,"Отложить")&&enabled(mc,"Продолжить")&&enabled(mc,"Отменить цель"),"A held goal must offer defer, resume and cancel");
            }
            case 11 -> { shot(mc,"oleg-tasks-goal.png");press(mc,"Отложить"); }
            case 12 -> {
                check(enabled(mc,"Продолжить")&&!enabled(mc,"Отложить")&&enabled(mc,"Отменить цель"),"A deferred goal offers resume and cancel only");
                shot(mc,"oleg-tasks-goal-deferred.png");
                press(mc,"Продолжить");
                // The same server thread runs the button's resume first, which zeroes the counters; then the milestone has spent its attempts.
                onServer(mc,(player,oleg) -> CompanionAgenda.goal(player.server,player.getUUID()).counter("food")[0]=12);
            }
            case 13 -> {
                shot(mc,"oleg-tasks-goal-blocked.png");
                for (int i=0;i<4;i++)mc.screen.mouseScrolled(mc.screen.width*3/4,mc.screen.height/2,0,-1);
                onServer(mc,(player,oleg) -> { var view=CompanionAgendaView.of(oleg);seen=view.state()+"|"+view.obstacle(); });
            }
            case 14 -> {
                check(seen.startsWith("BLOCKED|")&&seen.contains("попытки исчерпаны"),"The spent milestone did not stop the goal: "+seen);
                shot(mc,"oleg-tasks-goal-tree-end.png");
                for (int i=0;i<4;i++)mc.screen.mouseScrolled(mc.screen.width*3/4,mc.screen.height/2,0,1);
                mc.options.guiScale().set(4);mc.resizeDisplay();
            }
            case 15 -> { shot(mc,"oleg-tasks-goal-compact.png");mc.options.guiScale().set(3);mc.resizeDisplay(); }
            case 16 -> {
                check(enabled(mc,"Продолжить")&&enabled(mc,"Отменить цель"),"A stopped goal offers resume and cancel");
                press(mc,"Отменить цель");
                onServer(mc,(player,oleg) -> seen=String.valueOf(CompanionAgenda.goal(player.server,player.getUUID()).state()));
            }
            case 17 -> {
                check(seen.equals("CANCELLED"),"Cancel button did not cancel the goal: "+seen);
                check(mc.screen.children().stream().noneMatch(w->w instanceof Button b&&b.getMessage().getString().startsWith("Цель:")),"The cancelled goal is still listed");
                mc.screen.keyPressed(GLFW.GLFW_KEY_J,0,0);
                check(mc.screen==null,"J did not close task interface");
                CompanionActionExecutor.cancelPendingPlans();
                AICompanionMod.LOGGER.info("TASKS_UI_PROBE_PASSED: J open/close, actual priority, selective cancellation, full/brief report, compact layout, inventory close and tabs, long goal tree with defer/resume/cancel");
                mc.stop();
            }
        }
    }
    private static void check(boolean condition,String error) { if(!condition)throw new IllegalStateException(error); }
    private static void press(Minecraft mc,String prefix) {
        mc.screen.children().stream().filter(w->w instanceof Button b&&b.active&&b.getMessage().getString().startsWith(prefix))
                .map(w->(Button)w).findFirst().orElseThrow(()->new IllegalStateException("No enabled button: "+prefix)).onPress();
    }
    private static boolean enabled(Minecraft mc,String prefix) {
        return mc.screen.children().stream().anyMatch(w->w instanceof Button b&&b.active&&b.getMessage().getString().startsWith(prefix));
    }
    /** Runs on the integrated server thread with the player and his companion; a failure there is reported by the next phase. */
    private static void onServer(Minecraft mc,BiConsumer<ServerPlayer,CompanionEntity> job) {
        var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
        server.execute(() -> {
            try {
                var player=server.getPlayerList().getPlayer(id);
                var oleg=CompanionEvents.getOwnedCompanion(player);
                check(oleg!=null,"No companion in the probe world");
                job.accept(player,oleg);
            } catch (RuntimeException ex) { serverFailure=ex.toString(); }
        });
    }
    private static void shot(Minecraft mc,String name) {
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->AICompanionMod.LOGGER.info("Task UI screenshot: {}",message.getString()));
    }
}

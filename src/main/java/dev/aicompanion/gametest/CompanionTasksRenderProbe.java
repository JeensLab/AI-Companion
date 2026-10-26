package dev.aicompanion.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import java.util.List;

/** Opt-in UI integration and screenshot checks in a disposable offline world. Never shipped in the mod JAR. */
@EventBusSubscriber(modid=AICompanionMod.MOD_ID, value=Dist.CLIENT)
public final class CompanionTasksRenderProbe {
    private static boolean started;
    private static int phase, ticks;
    private static long chosen;
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
                press(mc,"Задачи");mc.screen.keyPressed(GLFW.GLFW_KEY_J,0,0);
                check(mc.screen==null,"J did not close task interface");
                CompanionActionExecutor.cancelPendingPlans();
                AICompanionMod.LOGGER.info("TASKS_UI_PROBE_PASSED: J open/close, actual priority, selective cancellation, full/brief report, compact layout, inventory close and tabs");
                mc.stop();
            }
        }
    }
    private static void check(boolean condition,String error) { if(!condition)throw new IllegalStateException(error); }
    private static void press(Minecraft mc,String prefix) {
        mc.screen.children().stream().filter(w->w instanceof Button b&&b.active&&b.getMessage().getString().startsWith(prefix))
                .map(w->(Button)w).findFirst().orElseThrow(()->new IllegalStateException("No enabled button: "+prefix)).onPress();
    }
    private static void shot(Minecraft mc,String name) {
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->AICompanionMod.LOGGER.info("Task UI screenshot: {}",message.getString()));
    }
}

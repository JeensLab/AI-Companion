package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionFailureMessages;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionQuietWorkTests {
    private static final String IRON = "Добыл 1 из 4 блока «железная руда»: в сумке прибавилось 1 «Рудное железо». Больше доступной руды рядом нет. В сумке: 99 Палка, 12 Дубовое бревно.";
    @GameTest(template="empty")
    public static void expandedArmourOrderHasOneShortFailureInsteadOfAList(GameTestHelper h) {
        var plan=List.of(new PlannedAction("obtain_item",0,1,"minecraft:iron_chestplate"),new PlannedAction("obtain_item",0,1,"minecraft:iron_leggings"));
        String raw="Скрафтил шлем. В сумке: много вещей.\nПлан остановлен: "+IRON+"\nНе выполнено: переплавить 8 железа, сделать нагрудник, проверить, добыть поножи, добыть ботинки.";
        String spoken=CompanionDialogue.summarize(plan,raw);
        h.assertTrue(spoken.startsWith("Накопал только 1 из 4") && spoken.length()<140,"Wrong partial outcome: "+spoken);
        h.assertTrue(!spoken.contains("Не успел")&&!spoken.contains("сумке")&&!spoken.contains("понож")&&!spoken.contains("Скрафтил"),"Technical roll call leaked: "+spoken);
        h.succeed();
    }
    @GameTest(template="empty")
    public static void repeatedFailuresIgnoreChangingCountsAndInventory(GameTestHelper h) throws Exception {
        var c=companion(h);finish(c,"IRON",CompanionActionResult.failed(IRON));int serial=c.autonomyLineSerial();
        String said=c.autonomyLine();
        h.assertTrue(serial>0 && said.length()<120&&!said.contains("сумке")&&!said.contains("минут")&&!said.contains("1 из"),"Robotic autonomous speech: "+said);
        reload(c,true);finish(c,"IRON",CompanionActionResult.failed(IRON.replace("1 из 4","2 из 4").replace("99 Палка","7 Уголь")));
        h.assertTrue(c.autonomyLineSerial()==serial,"Same problem announced after backoff/reload");h.succeed();
    }
    @GameTest(template="empty")
    public static void actualSuccessAllowsANewFailureNotice(GameTestHelper h) throws Exception {
        var c=companion(h);finish(c,"IRON",CompanionActionResult.failed(IRON));int before=c.autonomyLineSerial();
        finish(c,"IRON",CompanionActionResult.ok("Добыл железо."));reload(c,true);
        finish(c,"IRON",CompanionActionResult.failed(IRON));
        h.assertTrue(c.autonomyLineSerial()==before+1,"A new problem after real recovery was hidden");h.succeed();
    }
    @GameTest(template="empty")
    public static void newBlockerIsReportedButCancellationIsSilent(GameTestHelper h) throws Exception {
        var c=companion(h);finish(c,"IRON",CompanionActionResult.failed(IRON));int before=c.autonomyLineSerial();reload(c,true);
        finish(c,"IRON",CompanionActionResult.blocked("В сумке нет места."));
        h.assertTrue(c.autonomyLineSerial()==before+1 && c.autonomyLine().contains("сумка забита"),"A different actionable problem hidden");
        reload(c,true);finish(c,"IRON",CompanionActionResult.cancelled("Прервано."));
        h.assertTrue(c.autonomyLineSerial()==before+1,"Cancellation generated a complaint");h.succeed();
    }
    @GameTest(template="empty")
    public static void loadingCannotResetSpeechCooldownOrReviveOldComplaints(GameTestHelper h) throws Exception {
        var c=companion(h);finish(c,"IRON",CompanionActionResult.failed(IRON));int before=c.autonomyLineSerial();reload(c,false);
        finish(c,"WOOD",CompanionActionResult.failed("Не нашёл доступного дерева."));
        h.assertTrue(c.autonomyLineSerial()==before,"Reload reset global speech spacing");
        CompoundTag old=new CompoundTag();c.addAdditionalSaveData(old);old.remove("AutonomyReportedFailures");old.putInt("AutonomySpeechDelay",0);
        c.readAdditionalSaveData(old);finish(c,"WOOD",CompanionActionResult.failed("Не нашёл доступного дерева."));
        h.assertTrue(c.autonomyLineSerial()==before,"Upgrade replayed an existing five-minute complaint");h.succeed();
    }
    @GameTest(template="empty")
    public static void failureFactsAndRetryDelayRemainWhileSpeechIsQuiet(GameTestHelper h) throws Exception {
        var c=companion(h);finish(c,"WOOD",CompanionActionResult.failed("Не нашёл доступного дерева. Вещи сохранил."));
        var saved=new CompoundTag();c.addAdditionalSaveData(saved);
        h.assertTrue(saved.getCompound("AutonomyBackoff").getInt("WOOD")==CompanionAutonomy.FAILURE_BREAK_TICKS,"Physical retry delay changed");
        h.assertTrue(saved.getCompound("AutonomyReportedFailures").getString("WOOD").equals("WOOD"),"Failure memory absent");
        h.assertTrue(!(boolean)field(c.autonomy(),"explain"),"An automatic failure scheduled another verbose explanation");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void naturalFailuresNeverCopyTechnicalTail(GameTestHelper h) {
        for(String problem:List.of("Не нашёл доступного дерева.","Не хватает материалов.","Не нашёл безопасного хода к 46.","Непонятная ошибка механизма.")) {
            String speech=CompanionFailureMessages.explain("mine_ore",problem+" В сумке: minecraft:iron_ingot, 33 Палка. Координаты: -43, 64, -288.");
            h.assertTrue(speech.length()<120&&!speech.contains("minecraft")&&!speech.contains("33")&&!speech.contains("46")&&!speech.contains("Координаты"),speech);
        }
        h.assertTrue(CompanionFailureMessages.kind("В сумке нет места.")==CompanionFailureMessages.Kind.BAG,"Missed full inventory");h.succeed();
    }
    @GameTest(template="empty")
    public static void multilineFailureCannotTurnIntoAFalseSuccess(GameTestHelper h) {
        String spoken=CompanionDialogue.summarize(List.of(new PlannedAction("craft_item",0,1,"minecraft:iron_sword")),
                "План остановлен: Не хватает материалов.\nВ сумке: 32 minecraft:dirt.\nКоординаты: -43, 64, -288.");
        h.assertTrue(spoken.equals("На всё пока не хватает материалов."),"Multiline report became false success or technical speech: "+spoken);h.succeed();
    }
    @GameTest(template="empty")
    public static void inventoryWordsCannotInventAFailureReason(GameTestHelper h) {
        String spoken=CompanionDialogue.summarize(List.of(new PlannedAction("place_block",0,1,"minecraft:chest")),
                "План остановлен: Не удалось завершить работу. В сумке: 11 брёвен, 32 камня, 4 рудного железа.");
        h.assertTrue(spoken.equals("С этим пока не справился."),"Inventory invented a blocker: "+spoken);h.succeed();
    }
    private static CompanionEntity companion(GameTestHelper h) {
        var c=h.spawn(AICompanionMod.COMPANION.get(),2,1,2);c.setNoAi(true);return c;
    }
    private static void reload(CompanionEntity c,boolean expireDelay) {
        var saved=new CompoundTag();c.addAdditionalSaveData(saved);
        if(expireDelay){saved.putInt("AutonomySpeechDelay",0);saved.put("AutonomyBackoff",new CompoundTag());}
        c.readAdditionalSaveData(saved);
    }
    private static Object field(Object object,String name) throws Exception {
        var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void finish(CompanionEntity c,String deed,CompanionActionResult result) throws Exception {
        var type=(Class<? extends Enum>)Class.forName("dev.aicompanion.entity.CompanionAutonomy$Deed");
        var method=CompanionAutonomy.class.getDeclaredMethod("finished",type,CompanionActionResult.class);method.setAccessible(true);
        method.invoke(c.autonomy(),Enum.valueOf(type,deed),result);
    }
}

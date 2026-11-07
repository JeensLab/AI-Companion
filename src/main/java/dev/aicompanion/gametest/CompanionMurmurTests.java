package dev.aicompanion.gametest;

import dev.aicompanion.*;
import dev.aicompanion.CompanionConversation.Delivery;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

/** Idle murmurs: at most fifteen words, no digits, chat without voice out of earshot, tempo untouched. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMurmurTests {
    private static final String FIFTEEN = "раз два три четыре пять шесть семь восемь девять десять одиннадцать двенадцать тринадцать четырнадцать пятнадцать";

    @GameTest(template="empty")
    public static void murmurFitsOnlyUpToFifteenWordsWithoutDigits(GameTestHelper h) {
        h.assertTrue(CompanionConversation.fitsMurmur(FIFTEEN),"Fifteen words were refused");
        h.assertTrue(!CompanionConversation.fitsMurmur(FIFTEEN+" шестнадцать"),"Sixteen words were accepted");
        h.assertTrue(!CompanionConversation.fitsMurmur("Я на 120 блоках к северу"),"A number was accepted");
        h.assertTrue(!CompanionConversation.fitsMurmur("(12, 64, -30)"),"Coordinates were accepted");
        h.assertTrue(!CompanionConversation.fitsMurmur(null)&&!CompanionConversation.fitsMurmur("  "),"An empty murmur was accepted");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void longMurmurIsCutToWholeSentencesOrDropped(GameTestHelper h) {
        String first = "Тихо тут сегодня.";
        String cut = CompanionConversation.fitMurmur(first+" "+FIFTEEN+".");
        h.assertTrue(first.equals(cut),"Only the first sentence should stay, got: "+cut);
        h.assertTrue(FIFTEEN.equals(CompanionConversation.fitMurmur(FIFTEEN)),"A fitting murmur was changed");
        h.assertTrue(CompanionConversation.fitMurmur("Я на 120 блоках к северу. Тихо тут.")==null,"A first sentence with digits was kept");
        h.assertTrue(CompanionConversation.fitMurmur(FIFTEEN+" шестнадцать. Тихо.")==null,"A first sentence that is too long was kept");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void expiredUrgentRemarkNoLongerHoldsBackMurmurs(GameTestHelper h) {
        var c=new CompanionConversation();c.reset(0,0);
        long t=5_000;
        h.assertTrue(!c.takeObservation(1,0,t,false,false,0,true)&&c.urgentPending(t)==1,"A fresh urgent remark was not held");
        h.assertTrue(c.urgentPending(t+CompanionConversation.URGENT_TTL)==1,"The urgent remark expired too early");
        h.assertTrue(c.urgentPending(t+CompanionConversation.URGENT_TTL+1)==0&&c.urgentPending()==0,"An expired urgent remark still held back murmurs");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void ownTopicThatIsNotAMurmurNeverEntersTheBank(GameTestHelper h) {
        var soul=new dev.aicompanion.entity.CompanionSoul();
        var kept=java.util.List.of("Как там Барсик, чилит?");
        soul.adapt(java.util.Map.of(),java.util.List.of("Как думаешь, зачем мы вообще ходим так далеко от дома, и что нас там ждёт дальше?",
                "Ты помнишь, как мы нашли алмазы на 12 уровне?",kept.getFirst()));
        h.assertTrue(soul.ownTopics().equals(kept),"A long or numbered own topic was kept: "+soul.ownTopics());
        h.succeed();
    }

    @GameTest(template="empty")
    public static void everyBankTopicFitsAMurmur(GameTestHelper h) {
        for(var topic:CompanionTopics.ALL)
            h.assertTrue(CompanionConversation.fitsMurmur(topic.text()),"Bank topic is not a murmur: "+topic.text());
        h.succeed();
    }

    @GameTest(template="empty")
    public static void farAwayMurmurStaysInChatWithoutVoice(GameTestHelper h) {
        h.assertTrue(CompanionConversation.decide(true,false,false,false)==Delivery.CHAT,"Out of earshot the line should stay in chat");
        h.assertTrue(CompanionConversation.decide(true,true,false,false)==Delivery.VOICE,"Within earshot the line should be voiced");
        h.assertTrue(CompanionConversation.decide(false,true,false,false)==Delivery.DROP,"An unsafe moment should drop the line");
        h.assertTrue(CompanionConversation.decide(true,true,true,false)==Delivery.DROP,"An open screen should drop the line");
        h.assertTrue(CompanionConversation.decide(true,false,false,true)==Delivery.DROP,"A paused game should drop the line");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void realMinimumGapBetweenInitiativeMurmursIsSixtySeconds(GameTestHelper h) {
        h.assertTrue(CompanionConversation.MIN_GAP==60*20&&CompanionConversation.MAX_GAP==120*20,"The initiative interval changed");
        var c=new CompanionConversation();c.reset(0,0);
        long t=10_000;
        c.initiativeDelivered("Дождь вовремя.",t,0);
        h.assertTrue(!c.ready(t+CompanionConversation.MIN_GAP-1)&&c.ready(t+CompanionConversation.MIN_GAP),"The minimum gap between murmurs changed");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void ambientDirectionsTellTheModelTheMurmurLimit(GameTestHelper h) {
        String limit="не больше "+CompanionConversation.MURMUR_WORDS+" слов";
        for(boolean question:new boolean[]{true,false}) {
            String ordinary=dev.aicompanion.client.OpenRouterClient.ambientDirections(false,question);
            h.assertTrue(ordinary.contains(limit)&&ordinary.contains("без цифр")&&ordinary.contains("одна или две фразы"),"The murmur limit is not in the directions: "+ordinary);
        }
        String urgent=dev.aicompanion.client.OpenRouterClient.ambientDirections(true,false);
        h.assertTrue(!urgent.contains("без цифр"),"An urgent warning was told to avoid digits");
        String free=dev.aicompanion.client.OpenRouterClient.ambientDirections(false,true,false);
        h.assertTrue(!free.contains("без цифр")&&!free.contains(limit),"The limit was told to the model with short murmurs off");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void withShortMurmursOffALongMurmurIsNotCutOrRoutedToChat(GameTestHelper h) {
        String longText="Тихо тут сегодня. "+FIFTEEN+". Я на 120 блоках к северу.";
        h.assertTrue(longText.equals(CompanionConversation.fitMurmur(longText,false)),"A long murmur was cut with the limit off");
        h.assertTrue(CompanionConversation.fitMurmur(longText,true)!=null&&!longText.equals(CompanionConversation.fitMurmur(longText,true)),"The limit stopped cutting with it on");
        h.assertTrue(CompanionConversation.fitMurmur("  ",false)==null,"A blank murmur was kept");
        h.assertTrue(CompanionConversation.decide(true,false,false,false,false)==Delivery.DROP,"Out of earshot the line went to chat with the limit off");
        h.assertTrue(CompanionConversation.decide(true,true,false,false,false)==Delivery.VOICE,"Within earshot the line was not voiced with the limit off");
        h.assertTrue(CompanionConversation.decide(true,false,false,false,true)==Delivery.CHAT,"The chat route is gone with the limit on");
        var soul=new dev.aicompanion.entity.CompanionSoul();
        String topic="Как думаешь, зачем мы вообще ходим так далеко от дома, и что нас там ждёт дальше?";
        soul.adapt(java.util.Map.of(),java.util.List.of(topic),false);
        h.assertTrue(soul.ownTopics().equals(java.util.List.of(topic)),"A long own topic was refused with the limit off: "+soul.ownTopics());
        h.succeed();
    }

    @GameTest(template="empty")
    public static void murmurIntervalScalesTheGapAndKeepsTheDefault(GameTestHelper h) {
        for(int j=0;j<2000;j++) {
            h.assertTrue(CompanionConversation.gap(j,100)==CompanionConversation.gap(j),"The default interval changed the gap");
            h.assertTrue(CompanionConversation.gap(j,200)==2*CompanionConversation.gap(j),"The interval did not double the gap");
            h.assertTrue(CompanionConversation.topicGap(CompanionConversation.Scene.IDLE,j,100)==CompanionConversation.topicGap(CompanionConversation.Scene.IDLE,j),"The default interval changed the topic gap");
            h.assertTrue(CompanionConversation.topicGap(CompanionConversation.Scene.IDLE,j,50)==CompanionConversation.topicGap(CompanionConversation.Scene.IDLE,j)/2,"The interval did not halve the topic gap");
        }
        h.assertTrue(CompanionConversation.topicGap(CompanionConversation.Scene.FIGHT,0,400)==Integer.MAX_VALUE,"A fight topic gap overflowed");
        var c=new CompanionConversation();c.reset(0,0);c.setIntervalPercent(200);
        long t=10_000;
        c.initiativeDelivered("Дождь вовремя.",t,0);
        h.assertTrue(!c.ready(t+2*CompanionConversation.MIN_GAP-1)&&c.ready(t+2*CompanionConversation.MIN_GAP),"The conversation ignored the interval");
        h.succeed();
    }
}

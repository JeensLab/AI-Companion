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
}

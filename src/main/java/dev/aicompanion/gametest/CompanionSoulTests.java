package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.*;
import dev.aicompanion.CompanionConversation.Scene;
import dev.aicompanion.CompanionPersonality.Trait;
import dev.aicompanion.client.CompanionLocalCommands;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.HashSet;
import java.util.random.RandomGenerator;

/** Oleg's soul: his own topics, their pace, his memory of the player and its file. Local policies only, never cloud APIs. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSoulTests {
    private static final RandomGenerator FIRST = () -> 0L;
    @GameTest(template="empty")
    public static void theBankIsLargeVariedShortAndPlatformSafe(GameTestHelper h) {
        var keys=new HashSet<String>(); var categories=new HashSet<String>();
        for(var topic:CompanionTopics.ALL) {
            h.assertTrue(keys.add(topic.key()),"Repeated topic: "+topic.text()); categories.add(topic.category());
            h.assertTrue(topic.text().length()<=110&&CompanionSoul.speakable(topic.text()),"Long, multi-question or banned opener: "+topic.text());
        }
        h.assertTrue(CompanionTopics.ALL.size()>=120&&categories.size()>=20,"Too few or too narrow openers: "+CompanionTopics.ALL.size()+"/"+categories.size());
        for(String banned:new String[]{"Го покурим за гаражами?","Как тебе последние выборы президента?","Скажи свой адрес, бро.","Кринж, а где порно смотреть?"})
            h.assertTrue(!CompanionSoul.speakable(banned),"Banned subject spoken: "+banned);
        for(String fine:new String[]{"Была на выставке роботов, рил имба?","Экстремальный спорт — это база?","Мой косяк, бро, го дальше."})
            h.assertTrue(CompanionSoul.speakable(fine),"Harmless line blocked: "+fine);
        h.succeed();
    }
    @GameTest(template="empty")
    public static void noTopicIsRaisedTwiceUntilTheBankRunsOut(GameTestHelper h) {
        var soul=new CompanionSoul(); var raised=new java.util.ArrayList<String>(); var random=new java.util.Random(7);
        for(int i=0;i<CompanionTopics.ALL.size();i++) {
            var topic=soul.nextTopic(random); h.assertTrue(!raised.contains(topic.key()),"Topic repeated: "+topic.text()); raised.add(topic.key()); soul.raised(topic,1000L+i);
        }
        var again=soul.nextTopic(random);
        h.assertTrue(again.key().equals(raised.getFirst()),"After the bank the topic raised longest ago is not first: "+again.text());
        var reloaded=CompanionSoul.load(soul.save());
        h.assertTrue(reloaded.nextTopic(random).key().equals(again.key()),"Oldest topic not first to come back after reload");
        var fresh=CompanionSoul.load(new CompanionSoul().save()); fresh.raised(CompanionTopics.ALL.getFirst(),1);
        var restored=CompanionSoul.load(fresh.save());
        for(int i=0;i<500;i++) h.assertTrue(!restored.nextTopic(random).key().equals(CompanionTopics.ALL.getFirst().key()),"Raised topic repeated after reload");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void aTalkIsAnalysedIntoMemoryAndNewTopicsOfHisOwn(GameTestHelper h) {
        var soul=new CompanionSoul(); var opener=CompanionTopics.ALL.getFirst();
        soul.raised(opener,0); soul.said(opener.text(),0);
        soul.heard("Сейчас фонк на репите, бро",1000); soul.said("База, фонк под копку — имба.",2000);
        soul.heard("Ещё люблю гонять на велике",3000); soul.talked();
        h.assertTrue(soul.takeTalk(3000+CompanionSoul.QUIET-1,true)==null,"Talk analysed while still going");
        String talk=soul.takeTalk(3000+CompanionSoul.QUIET,true);
        h.assertTrue(talk!=null&&talk.contains("Товарищ: Ещё люблю гонять на велике")&&talk.contains("Компаньон: База"),"Talk not handed to analysis: "+talk);
        h.assertTrue(soul.takeTalk(3000+CompanionSoul.QUIET*2,true)==null,"Talk under analysis taken twice");
        h.assertTrue(soul.taste(opener.category())==1,"Two answers did not raise the subject's weight");
        var analysis=JsonParser.parseString("""
            {"profile":"Слушает фонк, катается на велике, отвечает коротко.","style":"Коротко, с подколами.",
             "facts":["Любит фонк","Катается на велике","Курит вейп за школой"],
             "topics":["Бро, как там твой велик, не тильтанул?","Го покурим?","Что? Где? Когда?","%s","Глянь https://example.com рил",
                       "Какой фонк-трек сейчас качает тебя больше всего?"]}""".formatted("Слушай ".repeat(30))).getAsJsonObject();
        h.assertTrue(soul.absorb(analysis,200000),"Valid analysis rejected");
        String context=soul.promptContext();
        h.assertTrue(context.contains("фонк")&&context.contains("Катается на велике")&&!context.contains("вейп"),"Memory missing or banned fact kept: "+context);
        var own=soul.nextTopic(FIRST);
        h.assertTrue(own.category().equals("own")&&own.text().equals("Бро, как там твой велик, не тильтанул?"),"His own topic not preferred: "+own);
        soul.raised(own,200001); var next=soul.nextTopic(FIRST);
        h.assertTrue(next.text().equals("Какой фонк-трек сейчас качает тебя больше всего?"),"Unsafe or malformed topic kept: "+next.text());
        soul.raised(next,200002); h.assertTrue(!soul.nextTopic(FIRST).category().equals("own"),"Only two of six topics were valid");
        soul.absorb(JsonParser.parseString("{\"topics\":[\"Бро, как там твой велик, не тильтанул?!\"]}").getAsJsonObject(),300000);
        h.assertTrue(!soul.nextTopic(FIRST).category().equals("own"),"An own topic already raised came back reworded");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void characterShiftsTowardsThePlayerStepByStepButTonesDownAtOnce(GameTestHelper h) {
        var soul=new CompanionSoul();
        String original=CompanionPersonality.style(soul.character());
        h.assertTrue(original.contains("Ты дерзкий")&&original.contains("Уместны «бля»")&&original.contains("Краткость — сестра таланта")&&original.contains("Twitch"),"Original Oleg changed");
        var wanted=JsonParser.parseString("{\"character\":{\"swearing\":2,\"humor\":2,\"tease\":-2,\"warmth\":\"много\",\"energy\":7,\"slang\":1.5,\"curiosity\":-1,\"rudeness\":2}}").getAsJsonObject();
        h.assertTrue(soul.absorb(wanted,1),"Character alone is not a usable analysis");
        var c=soul.character();
        h.assertTrue(c.get(Trait.SWEARING)==1&&c.get(Trait.HUMOR)==1&&c.get(Trait.CURIOSITY)==-1,"Not one step per talk: "+c);
        h.assertTrue(c.get(Trait.TEASE)==-2,"Toning down the teasing waited for more talks: "+c);
        h.assertTrue(c.get(Trait.WARMTH)==0&&c.get(Trait.ENERGY)==0&&c.get(Trait.SLANG)==0,"Malformed level kept: "+c);
        soul.absorb(wanted,2); c=soul.character();
        h.assertTrue(c.get(Trait.SWEARING)==2&&c.get(Trait.HUMOR)==2,"Second talk did not continue the shift: "+c);
        String adapted=CompanionPersonality.style(c);
        h.assertTrue(adapted.contains("Мат — обычная часть речи")&&adapted.contains("Без подколов")&&!adapted.contains("Ты дерзкий")
                &&adapted.contains("Краткость — сестра таланта")&&adapted.contains("зумерским сленгом")&&adapted.contains("Twitch"),"Adapted text wrong or fixed rules lost");
        soul.absorb(JsonParser.parseString("{\"character\":{\"swearing\":-2}}").getAsJsonObject(),3);
        h.assertTrue(soul.character().get(Trait.SWEARING)==-2&&CompanionPersonality.style(soul.character()).contains("Не матерись совсем"),"Asked to stop swearing, still swears");
        h.assertTrue(CompanionSoul.load(soul.save()).character().equals(soul.character()),"Character lost on restart");
        var edited=CompanionSoul.load(JsonParser.parseString("{\"character\":{\"humor\":9,\"tease\":-1,\"x\":1}}").getAsJsonObject()).character();
        h.assertTrue(edited.get(Trait.TEASE)==-1&&edited.get(Trait.HUMOR)==0,"Hand-edited level not checked: "+edited);
        h.succeed();
    }
    @GameTest(template="empty")
    public static void unansweredOrdersAndFailedAnalysisNeverLoopForever(GameTestHelper h) {
        var soul=new CompanionSoul(); var opener=CompanionTopics.ALL.get(3);
        soul.raised(opener,0); soul.said(opener.text(),0);
        h.assertTrue(soul.takeTalk(CompanionSoul.QUIET,true)==null&&soul.taste(opener.category())==-1,"Ignored opener analysed or not counted");
        soul.heard("добудь камня",10); soul.said("Окей, иду за камнем.",20);
        h.assertTrue(soul.takeTalk(20+CompanionSoul.QUIET,true)==null,"An order without conversation became a paid analysis");
        soul.heard("Как дела?",100); soul.talked();
        h.assertTrue(soul.takeTalk(100+CompanionSoul.QUIET,false)==null&&soul.takeTalk(100+CompanionSoul.QUIET,true)==null,"Talk kept although analysis is off");
        soul.heard("Как дела?",1000); soul.talked();
        long now=1000+CompanionSoul.QUIET;
        for(int attempt=1;attempt<=3;attempt++) {
            h.assertTrue(soul.takeTalk(now,true)!=null,"Retry "+attempt+" lost the talk");
            h.assertTrue(!soul.absorb(null,now),"Empty analysis accepted");
            h.assertTrue(attempt==3||soul.takeTalk(now+CompanionSoul.RETRY-1,true)==null,"Retry without a pause");
            now+=CompanionSoul.RETRY;
        }
        h.assertTrue(soul.takeTalk(now,true)==null,"A failing analysis is retried for ever");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void memoryFileSurvivesRestartAndABrokenFileIsKeptAside(GameTestHelper h) throws Exception {
        var soul=new CompanionSoul(); soul.raised(CompanionTopics.ALL.get(5),1); soul.heard("Я люблю котов",2); soul.talked();
        soul.absorb(JsonParser.parseString("{\"profile\":\"Кошатник.\",\"facts\":[\"Есть кот Барсик\"],\"topics\":[\"Как там Барсик, чилит?\"]}").getAsJsonObject(),3);
        soul.heard("Ещё я рисую",4); soul.talked();
        var folder=java.nio.file.Files.createTempDirectory("oleh_ai_soul"); var file=folder.resolve("soul.json");
        CompanionSoulFiles.write(file,soul.save().toString());
        var loaded=CompanionSoulFiles.read(file);
        h.assertTrue(loaded.promptContext().contains("Есть кот Барсик")&&loaded.nextTopic(FIRST).text().equals("Как там Барсик, чилит?"),"Memory lost on restart");
        h.assertTrue(loaded.takeTalk(4+CompanionSoul.QUIET,true).contains("Ещё я рисую"),"Unfinished talk lost on restart");
        java.nio.file.Files.writeString(file,"{ сломано");
        var broken=CompanionSoulFiles.read(file);
        h.assertTrue(broken.promptContext().isEmpty()&&java.nio.file.Files.exists(folder.resolve("soul.broken.json"))&&!java.nio.file.Files.exists(file),"Broken memory overwritten");
        java.nio.file.Files.delete(folder.resolve("soul.broken.json")); java.nio.file.Files.delete(folder);
        var edited=CompanionSoul.load(JsonParser.parseString("{\"profile\":\"Любит политику и выборы\",\"facts\":[1,{},\"ok факт\"],\"ownTopics\":[\"Го в казино?\",\"Как жизнь, бро?\"],\"taste\":{\"music\":99}}").getAsJsonObject());
        h.assertTrue(!edited.promptContext().contains("политик")&&edited.promptContext().contains("ok факт")&&edited.nextTopic(FIRST).text().equals("Как жизнь, бро?")&&edited.taste("music")==4,"Hand-edited file not checked");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void scenesSetThePaceAndAFightNeverOpensATopic(GameTestHelper h) {
        h.assertTrue(CompanionConversation.scene(true,true,true,false,99999)==Scene.FIGHT,"Fight not first");
        h.assertTrue(CompanionConversation.scene(false,true,true,true,0)==Scene.MINE,"Mine not recognised");
        h.assertTrue(CompanionConversation.scene(false,false,true,true,0)==Scene.TRAVEL,"Road not recognised");
        h.assertTrue(CompanionConversation.scene(false,false,false,false,20*59)==Scene.WORK&&CompanionConversation.scene(false,false,false,false,20*60)==Scene.IDLE,"Idle threshold");
        for(int j=0;j<=2400;j++) {
            h.assertTrue(CompanionConversation.topicGap(Scene.TRAVEL,j)>=1200&&CompanionConversation.topicGap(Scene.TRAVEL,j)<=2400,"Road pace");
            h.assertTrue(CompanionConversation.topicGap(Scene.MINE,j)>=1500&&CompanionConversation.topicGap(Scene.MINE,j)<=3000,"Mine pace");
            h.assertTrue(CompanionConversation.topicGap(Scene.IDLE,j)>=1800&&CompanionConversation.topicGap(Scene.IDLE,j)<=3600,"Idle pace");
            h.assertTrue(CompanionConversation.topicGap(Scene.WORK,j)>=3600&&CompanionConversation.topicGap(Scene.WORK,j)<=6000,"Work pace");
        }
        var c=new CompanionConversation(); c.reset(0,0);
        h.assertTrue(!c.topicReady(1199,Scene.TRAVEL)&&c.topicReady(1200,Scene.TRAVEL),"Road topic not after a minute");
        h.assertTrue(!c.topicReady(3599,Scene.WORK)&&c.topicReady(3600,Scene.WORK),"Work topic too soon");
        h.assertTrue(!c.topicReady(1_000_000,Scene.FIGHT),"Topic in a fight");
        c.initiativeDelivered("Что слушаешь, бро?",2000,0);
        h.assertTrue(!c.topicReady(2000+CompanionConversation.ANSWER_WAIT-1,Scene.TRAVEL),"New topic over an open question");
        h.assertTrue(c.topicReady(2000+CompanionConversation.ANSWER_WAIT,Scene.TRAVEL),"Ignored question silenced him");
        c.initiativeDelivered("Прикинь, у осьминога три сердца.",6000,0);
        h.assertTrue(!c.topicReady(6000+2*1200-1,Scene.TRAVEL)&&c.topicReady(6000+2*1200,Scene.TRAVEL),"Ignored question did not double the next pause");
        c.initiativeDelivered("Шаурма или бургер?",9000,0);
        h.assertTrue(!c.topicReady(9000+3*1800-1,Scene.IDLE)&&c.topicReady(9000+3*1800,Scene.IDLE),"Second ignored question did not triple the pause");
        c.playerMessage(20000,0);
        h.assertTrue(!c.topicReady(21199,Scene.TRAVEL)&&c.topicReady(21200,Scene.TRAVEL),"An answer did not reset the pause");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void inAFightSmallTalkIsHushedButOrdersPass(GameTestHelper h) {
        h.assertTrue(CompanionLocalCommands.hushed(true,"Как дела, бро?")&&CompanionLocalCommands.hushed(true,"Что слушаешь?"),"Small talk in a fight");
        h.assertTrue(!CompanionLocalCommands.hushed(false,"Как дела, бро?"),"Hushed without a fight");
        for(String order:new String[]{"убей зомби","помоги!","беги ко мне","сзади крипер","построй дом","добудь камня"})
            h.assertTrue(!CompanionLocalCommands.hushed(true,order),"Order hushed: "+order);
        h.assertTrue(CompanionLocalCommands.FIGHT_HUSH.equals("Помолчи, я тут занят мобом."),"Phrase changed"); h.succeed();
    }
}

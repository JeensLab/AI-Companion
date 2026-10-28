package dev.aicompanion.gametest;

import com.google.gson.JsonArray;
import dev.aicompanion.*;
import dev.aicompanion.CompanionConversation.Scene;
import dev.aicompanion.CompanionPersonality.Trait;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionMemoryCodec;
import dev.aicompanion.client.CompanionMemoryFlow;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.entity.CompanionBonds;
import dev.aicompanion.entity.CompanionMemory;
import dev.aicompanion.entity.CompanionMemoryDigest;
import dev.aicompanion.entity.CompanionSoul;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.random.RandomGenerator;

/** Oleg's soul on top of the world memory: his own topics, their pace, his character and how it is kept. Local policies only, never cloud APIs. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSoulTests {
    private static final RandomGenerator FIRST = () -> 0L;
    @GameTest(template="empty")
    public static void theBankIsLargeVariedShortAndPlatformSafe(GameTestHelper h) {
        var keys=new HashSet<String>(); var categories=new HashSet<String>();
        for(var topic:CompanionTopics.ALL) {
            h.assertTrue(keys.add(topic.key()),"Repeated topic: "+topic.text()); categories.add(topic.category());
            h.assertTrue(topic.text().length()<=110&&CompanionTopics.speakable(topic.text())&&!CompanionMemory.isSensitive(topic.text()),"Long, multi-question or banned opener: "+topic.text());
        }
        h.assertTrue(CompanionTopics.ALL.size()>=120&&categories.size()>=20,"Too few or too narrow openers: "+CompanionTopics.ALL.size()+"/"+categories.size());
        for(String banned:new String[]{"Го покурим за гаражами?","Как тебе последние выборы президента?","Скажи свой адрес, бро.","Кринж, а где порно смотреть?"})
            h.assertTrue(!CompanionTopics.speakable(banned),"Banned subject spoken: "+banned);
        for(String fine:new String[]{"Была на выставке роботов, рил имба?","Экстремальный спорт — это база?","Мой косяк, бро, го дальше."})
            h.assertTrue(CompanionTopics.speakable(fine),"Harmless line blocked: "+fine);
        h.succeed();
    }
    @GameTest(template="empty")
    public static void noTopicIsRaisedTwiceUntilTheBankRunsOut(GameTestHelper h) {
        var soul=new CompanionSoul(); var raised=new ArrayList<String>(); var random=new Random(7);
        for(int i=0;i<CompanionTopics.ALL.size();i++) {
            var topic=soul.next(random); h.assertTrue(!raised.contains(topic.key()),"Topic repeated: "+topic.text()); raised.add(topic.key()); soul.raised(topic);
        }
        var again=soul.next(random);
        h.assertTrue(again.key().equals(raised.getFirst()),"After the bank the topic raised longest ago is not first: "+again.text());
        var reloaded=CompanionSoul.load(soul.save());
        h.assertTrue(reloaded.next(random).key().equals(again.key()),"Oldest topic not first to come back after reload");
        var fresh=new CompanionSoul(); fresh.raised(CompanionTopics.ALL.getFirst());
        var restored=CompanionSoul.load(fresh.save());
        for(int i=0;i<500;i++) h.assertTrue(!restored.next(random).key().equals(CompanionTopics.ALL.getFirst().key()),"Raised topic repeated after reload");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void condensingShiftsHisCharacterStepByStepAndGivesHimSafeTopics(GameTestHelper h) {
        var parsed=CompanionMemoryCodec.parse("""
            {"memories":[{"kind":"player_fact","text":"Слушает фонк"}],"call_me":"","likes":[],"dislikes":[],
             "topics":["Бро, как там твой велик, не тильтанул?","Го покурим?","Что? Где? Когда?","%s","Глянь https://example.com рил",
                       "Мой ключ sk-abcdefgh12345678 рил?","Какой фонк-трек сейчас качает тебя больше всего?"],
             "character":{"swearing":2,"humor":2,"tease":-2,"warmth":"много","energy":7,"slang":1.5,"curiosity":-1,"rudeness":2}}""".formatted("Слушай ".repeat(30)));
        h.assertTrue(parsed.valid()&&parsed.topics().equals(List.of("Бро, как там твой велик, не тильтанул?","Какой фонк-трек сейчас качает тебя больше всего?")),"Unsafe or malformed topic kept: "+parsed.topics());
        h.assertTrue(parsed.character().equals(Map.of(Trait.SWEARING,2,Trait.HUMOR,2,Trait.TEASE,-2,Trait.CURIOSITY,-1)),"Malformed level kept: "+parsed.character());
        var many=CompanionMemoryCodec.parse("{\"memories\":[],\"topics\":[\"Тема номер один, бро?\",\"Тема номер два, бро?\",\"Тема номер три, бро?\",\"Тема номер четыре, бро?\",\"Тема номер пять, бро?\",\"Тема номер шесть, бро?\"]}");
        h.assertTrue(many.topics().size()==CompanionMemoryCodec.MAX_TOPICS&&many.character().isEmpty(),"More than five topics kept or a character invented: "+many);
        var soul=new CompanionSoul();
        String original=CompanionPersonality.style(soul.character());
        for(String rule:new String[]{"Ты язвительный","Мат — привычная часть твоей речи","на содержательный вопрос отвечай подробнее","ценишь запас факелов",
                "Молчание не означает согласия","принятие поручения не означает его завершения","Не выдумывай биографию","без молодёжного сленга","Twitch"})
            h.assertTrue(original.contains(rule),"Oleg without the soul model changed: "+rule);
        // The owner's choice: no slang is demanded of Oleg as he is; it comes only once the player talks so himself.
        h.assertTrue(!original.contains("Обязательно говори с")&&!original.contains("рил"),"Slang demanded without the player using it");
        h.assertTrue(CompanionPersonality.style(Map.of(Trait.SLANG,2)).contains("Говори со сленгом")
                &&CompanionPersonality.style(Map.of(Trait.SLANG,-2)).contains("Без молодёжного сленга совсем"),"Slang does not follow the player");
        for(var topic:CompanionTopics.ALL)
            h.assertTrue(!topic.text().matches("(?iU).*(\\bрил\\b|\\bбаза\\b|кринж|вайб|\\bимб|\\bбро\\b|тильт|рофл|чекн|душнил|хейт|топов).*"),"Slang in a bank opener: "+topic.text());
        soul.adapt(parsed.character(),parsed.topics());
        var c=soul.character();
        h.assertTrue(c.get(Trait.SWEARING)==1&&c.get(Trait.HUMOR)==1&&c.get(Trait.CURIOSITY)==-1,"Not one step per talk: "+c);
        h.assertTrue(c.get(Trait.TEASE)==-2,"Toning down the teasing waited for more talks: "+c);
        h.assertTrue(c.get(Trait.WARMTH)==0&&c.get(Trait.ENERGY)==0&&c.get(Trait.SLANG)==0,"An untouched trait moved: "+c);
        soul.adapt(parsed.character(),List.of()); c=soul.character();
        h.assertTrue(c.get(Trait.SWEARING)==2&&c.get(Trait.HUMOR)==2,"Second talk did not continue the shift: "+c);
        String adapted=CompanionPersonality.style(c);
        h.assertTrue(adapted.contains("Мат — обычная часть почти каждой реплики")&&adapted.contains("Без подколов")&&!adapted.contains("Ты язвительный")
                &&adapted.contains("Молчание не означает согласия")&&adapted.contains("принятие поручения не означает его завершения")
                &&adapted.contains("без молодёжного сленга")&&adapted.contains("Twitch"),"Adapted text wrong or fixed rules lost");
        soul.adapt(Map.of(Trait.SWEARING,-2),List.of());
        h.assertTrue(soul.character().get(Trait.SWEARING)==-2&&CompanionPersonality.style(soul.character()).contains("Не матерись совсем"),"Asked to stop swearing, still swears");
        var own=soul.next(FIRST);
        h.assertTrue(own.category().equals("own")&&own.text().equals("Бро, как там твой велик, не тильтанул?"),"His own topic not preferred: "+own);
        soul.raised(own); var next=soul.next(FIRST);
        h.assertTrue(next.text().equals("Какой фонк-трек сейчас качает тебя больше всего?"),"Second own topic lost: "+next.text());
        soul.raised(next); h.assertTrue(!soul.next(FIRST).category().equals("own"),"Only two of the topics were valid");
        soul.adapt(Map.of(),List.of("Бро, как там твой велик, не тильтанул?!",CompanionTopics.ALL.getFirst().text()));
        h.assertTrue(soul.ownTopics().isEmpty(),"An own topic already raised came back reworded, or a bank topic was taken as his own: "+soul.ownTopics());
        h.succeed();
    }
    @GameTest(template="empty")
    public static void theSoulIsKeptInTheWorldMemoryAndForgottenWithIt(GameTestHelper h) {
        var server=h.getLevel().getServer(); UUID owner=UUID.randomUUID(); var bonds=CompanionBonds.get(server);
        var first=bonds.raiseTopic(owner,FIRST);
        bonds.recordTalk(owner,true,"Фонк на репите"); bonds.recordTalk(owner,false,"База."); bonds.recordTalk(owner,true,"Ещё люблю гонять на велике");
        var second=bonds.raiseTopic(owner,FIRST);
        h.assertTrue(!second.key().equals(first.key())&&bonds.memory(owner).soul().taste(first.category())==1,"Two answers did not raise the subject's weight");
        bonds.adapt(owner,Map.of(Trait.SWEARING,2),List.of("Как там Барсик, чилит?"));
        h.assertTrue(CompanionMemoryDigest.of(server,owner,true,0).character().get(Trait.SWEARING)==1,"The client does not get the shifted character");
        h.assertTrue(CompanionMemoryDigest.of(server,owner,false,0).character().isEmpty(),"The character is shown while memory is off");
        var saved=CompanionBonds.load(bonds.save(new CompoundTag(),server.registryAccess()),server.registryAccess()).memory(owner).soul();
        h.assertTrue(saved.character().get(Trait.SWEARING)==1&&saved.ownTopics().equals(List.of("Как там Барсик, чилит?"))&&saved.taste(first.category())==1
                &&saved.brief().contains(first.text())&&saved.brief().contains(second.text()),"The soul was not saved with the world: "+saved.save());
        var older=bonds.memory(owner).save(); older.remove("Soul");
        h.assertTrue(CompanionMemory.load(older).soul().isEmpty(),"A save from before the soul did not load as Oleg of main");
        CompoundTag edited=new CompoundTag(), traits=new CompoundTag(), taste=new CompoundTag(); ListTag own=new ListTag(), raised=new ListTag();
        traits.putInt("humor",9); traits.putInt("tease",-1); edited.put("Character",traits);
        for(String text:new String[]{"Го в казино?","Как жизнь, бро?","Мой ключ sk-abcdefgh12345678, рил?"}) own.add(StringTag.valueOf(text));
        edited.put("Own",own); raised.add(StringTag.valueOf("own:мой пароль от сервера qwerty")); raised.add(StringTag.valueOf("x")); edited.put("Raised",raised);
        taste.putInt("music",99); edited.put("Taste",taste);
        var loaded=CompanionSoul.load(edited);
        h.assertTrue(loaded.character().get(Trait.TEASE)==-1&&loaded.character().get(Trait.HUMOR)==0&&loaded.ownTopics().equals(List.of("Как жизнь, бро?"))
                &&loaded.taste("music")==CompanionSoul.MAX_TASTE&&!loaded.brief().contains("пароль"),"Hand-edited save not checked: "+loaded.save());
        bonds.forget(owner);
        h.assertTrue(bonds.memory(owner).soul().isEmpty()&&CompanionMemoryDigest.of(server,owner,true,0).character().values().stream().allMatch(level->level==0),
                "«Забудь всё» kept his character or topics");
        h.assertTrue(CompanionMemoryDigest.of(server,owner,true,0).withoutMemory().character().isEmpty(),"The client view kept the character after forgetting");
        h.succeed();
    }
    @GameTest(template="empty", batch="soul_condense", timeoutTicks=200)
    public static void topicsAndCharacterComeWithTheMemoryInOneRequest(GameTestHelper h) {
        var server=h.getLevel().getServer(); UUID owner=UUID.randomUUID(); List<JsonArray> requests=new CopyOnWriteArrayList<>();
        try {
            var bonds=CompanionBonds.get(server);
            CompanionMemoryFlow.useCompleter((messages,maxTokens,trace)->{ requests.add(messages); return CompletableFuture.completedFuture(
                    "{\"memories\":[{\"kind\":\"player_fact\",\"text\":\"Любит фонк\"}],\"call_me\":\"\",\"likes\":[],\"dislikes\":[],"
                    +"\"topics\":[\"Бро, какой фонк-трек качает тебя больше всего?\"],\"character\":{\"swearing\":2,\"humor\":1}}"); });
            OpenRouterClient.resetSession();
            CompanionMemoryFlow.bind(server,owner,true);
            bonds.adapt(owner,Map.of(Trait.HUMOR,1),List.of("Как там Барсик, чилит?"));
            CompanionMemoryFlow.companionSaid("topic","Что слушаешь, бро?");
            CompanionMemoryFlow.companionSaid("autonomous","Начался дождь.");
            for(int i=0;i<12;i++) CompanionMemoryFlow.playerSaid("Реплика игрока "+i,null);
            h.runAfterDelay(5,()->{
                try {
                    h.assertTrue(requests.size()==1,"Memory, topics and character took more than one request: "+requests.size());
                    String sent=requests.getFirst().toString();
                    h.assertTrue(sent.contains("Компаньон: Что слушаешь, бро?")&&!sent.contains("Начался дождь")&&sent.contains("Реплика игрока 11"),"His topic missing from the journal or a remark in it: "+sent);
                    h.assertTrue(sent.contains("humor=1")&&sent.contains("Как там Барсик, чилит?"),"The request does not carry his character and topics: "+sent);
                    h.assertTrue(sent.contains("ничего не утверждай о текущей обстановке")&&sent.contains("Суди только по репликам «Игрок»")&&sent.contains("Twitch"),"Topic and character rules missing: "+sent);
                    var memory=bonds.memory(owner);
                    h.assertTrue(memory.entries().stream().anyMatch(e->e.text().equals("Любит фонк")),"Memory not stored: "+memory.entries());
                    h.assertTrue(memory.soul().character().get(Trait.SWEARING)==1&&memory.soul().character().get(Trait.HUMOR)==1
                            &&memory.soul().ownTopics().equals(List.of("Как там Барсик, чилит?","Бро, какой фонк-трек качает тебя больше всего?")),"Topics or character not stored: "+memory.soul().save());
                    h.succeed();
                } finally {
                    resetFlow();
                }
            });
        } catch (RuntimeException error) {
            resetFlow();
            throw error;
        }
    }
    @GameTest(template="empty")
    public static void scenesSetThePaceAndAFightNeverOpensATopic(GameTestHelper h) {
        h.assertTrue(CompanionConversation.scene(true,true,true,false,99999)==Scene.FIGHT,"Fight not first");
        h.assertTrue(CompanionConversation.scene(false,true,true,true,0)==Scene.MINE,"Mine not recognised");
        h.assertTrue(CompanionConversation.scene(false,false,true,true,0)==Scene.TRAVEL,"Road not recognised");
        h.assertTrue(CompanionConversation.scene(false,false,false,false,20*59)==Scene.WORK&&CompanionConversation.scene(false,false,false,false,20*60)==Scene.IDLE,"Idle threshold");
        for(int j=0;j<=1200;j++) {
            h.assertTrue(CompanionConversation.topicGap(Scene.TRAVEL,j)>=1200&&CompanionConversation.topicGap(Scene.TRAVEL,j)<=2400,"Road pace");
            h.assertTrue(CompanionConversation.topicGap(Scene.MINE,j)>=1500&&CompanionConversation.topicGap(Scene.MINE,j)<=2700,"Mine pace");
            h.assertTrue(CompanionConversation.topicGap(Scene.IDLE,j)>=1800&&CompanionConversation.topicGap(Scene.IDLE,j)<=3000,"Idle pace");
            h.assertTrue(CompanionConversation.topicGap(Scene.WORK,j)>=3600&&CompanionConversation.topicGap(Scene.WORK,j)<=4800,"Work pace");
        }
        var c=new CompanionConversation(); c.reset(0,0);
        h.assertTrue(!c.topicReady(1199,Scene.TRAVEL)&&c.topicReady(1200,Scene.TRAVEL),"Road topic not after a minute");
        h.assertTrue(!c.topicReady(3599,Scene.WORK)&&c.topicReady(3600,Scene.WORK),"Work topic too soon");
        h.assertTrue(!c.topicReady(1_000_000,Scene.FIGHT),"Topic in a fight");
        c.talked(2000,0); c.initiativeDelivered("Прикинь, у осьминога три сердца.",2000,0);
        h.assertTrue(!c.topicReady(3199,Scene.TRAVEL)&&c.topicReady(3200,Scene.TRAVEL),"A topic without a question held up the next one");
        c.initiativeDelivered("Начался дождь.",3300,0);
        h.assertTrue(!c.topicReady(4499,Scene.IDLE)&&c.topicReady(4500,Scene.IDLE),"A remark on an event put his topic off past the next free slot");
        // Silence is no answer and no consent (main): no new topic of his own until the player speaks; remarks on events go on.
        c.talked(5000,0); c.initiativeDelivered("Что слушаешь, бро?",5000,0);
        h.assertTrue(!c.topicReady(5000+CompanionConversation.ANSWER_WAIT,Scene.TRAVEL)&&!c.topicReady(1_000_000,Scene.IDLE)&&!c.canAskQuestion(),"New topic over an unanswered question");
        h.assertTrue(c.ready(1_000_000),"Unanswered question silenced remarks on events");
        c.playerMessage(20000,0);
        h.assertTrue(c.canAskQuestion()&&!c.topicReady(21199,Scene.TRAVEL)&&c.topicReady(21200,Scene.TRAVEL),"An answer did not reset the pause");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void remarksOnEventsShareTheSlotInsteadOfCrowdingOutTopics(GameTestHelper h) {
        // Game 27.10: an event every one to two minutes reset the pause before a topic, and at work no topic came at all.
        for(var scene:new Scene[]{Scene.TRAVEL,Scene.MINE,Scene.IDLE,Scene.WORK}) {
            var c=new CompanionConversation(); c.reset(0,600); int serial=0, events=0, topics=0; long answer=-1;
            for(long t=1;t<=20*60*16;t++) {
                if(t==answer) c.playerMessage(t,600);
                if(t%(20*70)==0) { if(c.takeObservation(++serial,0,t,true,false,600)) { events++; c.initiativeDelivered("Событие номер "+serial,t,600); } }
                else if(c.topicReady(t,scene)) { topics++; c.talked(t,600); c.initiativeDelivered("Тема номер "+t+"?",t,600); answer=t+200; }
            }
            h.assertTrue(topics>=(scene==Scene.WORK?3:4)&&events>=2,"Topics crowded out by remarks on events in "+scene+": "+topics+" topics, "+events+" remarks");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void inAFightSmallTalkIsHushedButOrdersPass(GameTestHelper h) {
        h.assertTrue(CompanionLocalCommands.hushed(true,"Как дела, бро?")&&CompanionLocalCommands.hushed(true,"Что слушаешь?"),"Small talk in a fight");
        h.assertTrue(!CompanionLocalCommands.hushed(false,"Как дела, бро?"),"Hushed without a fight");
        for(String order:new String[]{"убей зомби","помоги!","беги ко мне","сзади крипер","построй дом","добудь камня","стой и жди","Стой! И не двигайся","жди 10 секунд","подожди тут","постой, крафчу",
                "дай мне еды","Отдай меч","кинь стрелы!","передай факелы","брось мне хлеб","верни кирку","держи щит"})
            h.assertTrue(!CompanionLocalCommands.hushed(true,order),"Order hushed: "+order);
        h.assertTrue(CompanionLocalCommands.FIGHT_HUSH.equals("Помолчи, я тут занят мобом."),"Phrase changed"); h.succeed();
    }
    @GameTest(template="tall_tree",timeoutTicks=100)
    public static void onlyAMonsterAfterOneOfThemIsAFight(GameTestHelper h) {
        for(int x=0;x<32;x++) for(int z=0;z<32;z++) h.setBlock(new BlockPos(x,0,z),Blocks.BEDROCK);
        var c=h.spawn(AICompanionMod.COMPANION.get(),16,1,16); c.setFollowing(false);
        var player=h.makeMockServerPlayerInLevel(); var spot=h.absolutePos(new BlockPos(14,1,16));
        player.teleportTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5); c.setOwnerId(player.getUUID());
        var piglin=h.spawn(EntityType.ZOMBIFIED_PIGLIN,20,1,16); piglin.setNoAi(true); piglin.setInvulnerable(true);
        h.runAtTickTime(40,()->{
            h.assertTrue(!c.fighting()&&!c.sociallySafe(),"A peaceful zombified piglin in sight became a fight, or chatter safe beside it");
            piglin.setTarget(player);
        });
        // The invulnerable piglin is removed, or it stays beside a later marathon arena: Oleg then fights it for ever and the
        // marathons failed «Иди ко мне» (3 of 5 full runs after PR #28, none before). The batch cleanup only takes companions.
        h.runAtTickTime(80,()->{boolean fight=c.fighting(); piglin.discard(); h.assertTrue(fight,"A monster after the player is no fight");h.succeed();});
    }
    private static void resetFlow() {
        CompanionMemoryFlow.useCompleter(null);
        CompanionMemoryFlow.unbind();
        OpenRouterClient.resetSession();
    }
}

package dev.aicompanion.gametest;

import com.google.gson.JsonObject;
import dev.aicompanion.*;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

/** Character/expression tests use real entities and local policies. Never call cloud APIs. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCharacterTests {
    @GameTest(template="empty")
    public static void dangerAndCombatOverrideHumorAndWork(GameTestHelper h) {
        var state = new CompanionExpressionState();
        state.react(CompanionExpression.SMIRK,100,120);
        h.assertTrue(state.update(100,false,false,true)==CompanionExpression.SMIRK,"Smirk lost during work");
        h.assertTrue(state.update(101,false,true,true)==CompanionExpression.COMBAT,"Joke overrides fight");
        h.assertTrue(state.update(102,true,true,true)==CompanionExpression.WORRIED,"Fight overrides low health");
        h.assertTrue(state.update(103,false,true,true)==CompanionExpression.WORRIED,"Danger flickered");
        h.assertTrue(state.update(143,false,true,true)==CompanionExpression.COMBAT,"Fight not restored");
        h.assertTrue(state.update(184,false,false,true)==CompanionExpression.SMIRK,"Event not restored");
        h.assertTrue(state.update(221,false,false,true)==CompanionExpression.FOCUSED,"Work not restored");
        h.assertTrue(state.update(252,false,false,false)==CompanionExpression.CALM,"Not calm again"); h.succeed();
    }
    @GameTest(template="empty")
    public static void shortInterruptionsDoNotFlickerAndOldEventsExpire(GameTestHelper h) {
        var s = new CompanionExpressionState();
        h.assertTrue(s.update(100,false,false,true)==CompanionExpression.FOCUSED,"No focus");
        h.assertTrue(s.update(129,false,false,false)==CompanionExpression.FOCUSED,"Focus flickered");
        h.assertTrue(s.update(130,false,false,false)==CompanionExpression.CALM,"Focus stuck");
        s.react(CompanionExpression.PLEASED,200,100);
        s.update(201,false,true,false);
        h.assertTrue(s.update(301,false,false,false)==CompanionExpression.CALM,"Expired success resurfaced after fight"); h.succeed();
    }
    @GameTest(template="empty")
    public static void speechMoodIsLocalAndPromisesAreNotVictories(GameTestHelper h) {
        h.assertTrue(CompanionExpression.speech("Хех, ну ты и гений. Шучу.")==CompanionExpression.SMIRK,"No humor cue");
        h.assertTrue(CompanionExpression.speech("Ура, здорово получилось!")==CompanionExpression.PLEASED,"No joy cue");
        h.assertTrue(CompanionExpression.speech("Ага, сейчас сделаю.")==CompanionExpression.CALM,"Promise is joy");
        h.assertTrue(CompanionExpression.speech("Ого, ничего себе!")==CompanionExpression.SURPRISED,"No surprise cue");
        h.assertTrue(CompanionExpression.speech("Вот досада, обидно.")==CompanionExpression.DISPLEASED,"No displeased cue");
        h.assertTrue(CompanionExpression.speech("Я недоволен.")==CompanionExpression.DISPLEASED,"Displeased treated as pleased");
        h.assertTrue(CompanionExpression.speech("Тут много камня.")==CompanionExpression.CALM,"Surprise cue matches a word fragment");
        h.assertTrue(CompanionExpression.fromId(99)==CompanionExpression.CALM,"Unknown ID not safe"); h.succeed();
    }
    @GameTest(template="empty")
    public static void newEmotionsRespectDangerAndExpire(GameTestHelper h) {
        var state = new CompanionExpressionState();
        state.react(CompanionExpression.SURPRISED,100,60);
        h.assertTrue(state.update(100,false,false,true)==CompanionExpression.SURPRISED,"Surprise lost during work");
        state.react(CompanionExpression.DISPLEASED,101,60);
        h.assertTrue(state.update(101,false,false,true)==CompanionExpression.DISPLEASED,"No displeased reaction");
        h.assertTrue(state.update(102,true,false,true)==CompanionExpression.WORRIED,"Emotion overrides danger");
        h.assertTrue(state.update(162,false,false,false)==CompanionExpression.CALM,"New emotion stuck");
        h.assertTrue(CompanionExpression.fromId(5)==CompanionExpression.PLEASED,"Existing expression IDs changed");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void mouthEnvelopeFollowsLocalAudioAndSilenceAtTheDeviceCursor(GameTestHelper h) throws Exception {
        int rate = 24000;
        byte[] samples = new byte[rate * 2];
        for (int frame = rate / 4; frame < rate * 3 / 4; frame++) {
            short value = (short)(Math.sin(frame * 2 * Math.PI * 220 / rate) * 16000);
            samples[frame * 2] = (byte)value;
            samples[frame * 2 + 1] = (byte)(value >> 8);
        }
        var wav = new java.io.ByteArrayOutputStream();
        var format = new javax.sound.sampled.AudioFormat(rate,16,1,true,false);
        try (var stream = new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(samples),format,rate)) {
            javax.sound.sampled.AudioSystem.write(stream,javax.sound.sampled.AudioFileFormat.Type.WAVE,wav);
        }
        var pcm = dev.aicompanion.client.VoicePcm.decode(wav.toByteArray());
        var envelope = new dev.aicompanion.client.CompanionSpeechEnvelope(pcm);
        h.assertTrue(envelope.atSample(-1)==0 && envelope.atSample(0)==0,"Mouth opens before audio");
        h.assertTrue(envelope.atSample(rate / 2)>.9F,"Speech does not open mouth");
        h.assertTrue(envelope.atSample(rate * 9 / 10)==0 && envelope.atSample(rate)==0,"Silence/end does not close mouth");
        float closed = 1;
        for(int i=0;i<20;i++) closed=dev.aicompanion.client.CompanionSpeechEnvelope.smooth(closed,0,.01);
        h.assertTrue(closed<.02F,"Release stays open across pauses");
        float attack=dev.aicompanion.client.CompanionSpeechEnvelope.smooth(0,1,.01);
        h.assertTrue(attack>0 && attack<1,"Attack is not smooth");
        var next = new dev.aicompanion.client.CompanionSpeechEnvelope(new dev.aicompanion.client.VoicePcm.Pcm(format,new byte[4800]));
        h.assertTrue(next.atSample(1200)==0,"Next silent reply inherits previous mouth");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void initiativeWindowsAreOneToTwoMinutesWithJitter(GameTestHelper h) {
        var c = new CompanionConversation(); c.reset(100,0);
        h.assertTrue(!c.ready(1299)&&c.ready(1300),"Initial quiet window");
        c.initiativeDelivered("Работа идёт.",5000,1200); h.assertTrue(c.nextOpportunity()==7400&&!c.ready(7399)&&c.ready(7400),"Maximum initiative window");
        for(int i=0;i<10000;i++) h.assertTrue(CompanionConversation.gap(i)>=1200&&CompanionConversation.gap(i)<=2400,"Gap escaped bounds");
        c.initiativeDelivered("Начался дождь.",12000,777); h.assertTrue(c.nextOpportunity()==13977,"No jitter"); h.succeed();
    }
    @GameTest(template="empty")
    public static void ignoredQuestionExpiresWithoutConsentOrAnotherQuestion(GameTestHelper h) {
        var c = new CompanionConversation(); c.reset(0,0); c.delivered("Дальше за железом?",4000,0);
        h.assertTrue(!c.ready(4000+CompanionConversation.ANSWER_WAIT-1)&&c.awaitingAnswer(),"Unanswered question timed out into chatter");
        c.activity(5000,0); h.assertTrue(!c.ready(4000+CompanionConversation.ANSWER_WAIT-1),"Recording/reply activity closed the question");
        // Soul model: a question nobody answers is let go, so that he does not stay silent until the player speaks.
        h.assertTrue(c.ready(4000+CompanionConversation.ANSWER_WAIT),"Ignored question silenced him for good");
        c.playerMessage(200001,0); h.assertTrue(!c.awaitingAnswer()&&!c.ready(200002)&&c.ready(203601),"Answer not accounted for"); h.succeed();
    }
    @GameTest(template="empty")
    public static void repeatedJokesAndQuestionsAreRejectedLocally(GameTestHelper h) {
        var c = new CompanionConversation(); c.reset(0,0);
        c.delivered("Ну ты и гений, шучу!",10,0);
        h.assertTrue(!c.fresh("НУ ТЫ И ГЕНИЙ... ШУЧУ?"),"Punctuation bypass");
        h.assertTrue(!c.fresh("Ну ты гений, шучу!"),"Near-duplicate bypass");
        c.delivered("Дальше за железом или домой?",20,0);
        h.assertTrue(!c.fresh("Дальше за железом или домой?!"),"Repeated question");
        h.assertTrue(c.fresh("Утро наступило. Дождь наконец закончился."),"Different actual fact rejected"); h.succeed();
    }
    @GameTest(template="empty")
    public static void oneObservationCannotStartMultipleRequestsOrQueueBehindACommand(GameTestHelper h) {
        var c=new CompanionConversation();c.reset(0,0);c.trackObservation(5);
        h.assertTrue(!c.takeObservation(5,0,4000,true,false,0),"Tracking replayed old event");
        h.assertTrue(c.takeObservation(6,0,4000,true,false,0),"Fresh meaningful event rejected");
        h.assertTrue(!c.takeObservation(6,0,10000,true,false,0),"Same event starts second request");
        h.assertTrue(!c.takeObservation(7,0,10000,true,true,0),"Player command blocked by initiative");
        h.assertTrue(!c.takeObservation(7,0,10001,true,false,0),"Event queued behind command");
        h.assertTrue(!c.takeObservation(8,401,10002,true,false,0),"Stale observation replayed");
        h.assertTrue(c.takeObservation(9,-1,10003,true,false,0),"One tick of client/server clock skew ate the event");
        h.assertTrue(!c.takeObservation(9,0,14000,true,false,0),"Clock skew replayed the event");h.succeed();
    }
    @GameTest(template="empty")
    public static void lateAmbientRepliesAreDroppedAfterSpeechDangerOrWorldChange(GameTestHelper h) {
        var c=new CompanionConversation();c.reset(0,0);
        h.assertTrue(c.takeObservation(1,0,4000,true,false,0),"No initial request");long capture=c.revision();
        h.assertTrue(c.acceptReply(capture,"Хех, у кирки снова рабочий день.",true,false),"Valid reply lost");
        h.assertTrue(!c.acceptReply(capture,"Шучу.",false,false),"Danger ignored on arrival");
        h.assertTrue(!c.acceptReply(capture,"Шучу.",true,true),"Busy conversation interrupted");
        c.playerMessage(4001,0);h.assertTrue(!c.acceptReply(capture,"Шучу.",true,false),"Player message not prioritized");
        capture=c.revision();c.reset(5000,0);h.assertTrue(!c.acceptReply(capture,"Шучу.",true,false),"World change replayed response");
        h.assertTrue(!c.acceptReply(c.revision(),"Куда? И зачем?",true,false),"Several questions admitted");h.succeed();
    }
    @GameTest(template="empty")
    public static void recordingInvalidatesAmbientReplyWithoutAnsweringTheQuestion(GameTestHelper h) {
        var c=new CompanionConversation();c.reset(0,0);
        c.takeObservation(1,0,1200,true,false,0);long capture=c.revision();
        c.activity(1201,0);
        h.assertTrue(!c.acceptReply(capture,"Красота!",true,false),"Recording did not invalidate old reply");
        c.delivered("Тебе нравится этот инструмент?",1300,0);c.activity(1400,0);
        h.assertTrue(c.awaitingAnswer()&&!c.canAskQuestion(),"Failed recording counted as an answer");
        c.reset(2000,0);
        h.assertTrue(c.canAskQuestion()&&!c.awaitingAnswer()&&c.fresh("Тебе нравится этот инструмент?"),"World reset retained conversation state");h.succeed();
    }
    @GameTest(template="empty")
    public static void ambientHistoryKeepsQuestionsAndAnswersInCompactOrder(GameTestHelper h) {
        var history = new java.util.ArrayList<JsonObject>(); history.add(message("system","rules"));
        history.add(message("user","[Что вышло на деле: "+"x".repeat(3000)+"]"));
        for(int i=0;i<7;i++) history.add(message("assistant","старый разговор "+i));
        history.add(message("assistant","Дальше за железом?")); history.add(message("user","Да, но сначала домой."));
        history.add(message("assistant","x".repeat(2000)));
        var compact=OpenRouterClient.ambientHistory(history);
        h.assertTrue(compact.size()==6&&compact.get(3).get("content").getAsString().equals("Дальше за железом?")
                &&compact.get(4).get("content").getAsString().equals("Да, но сначала домой."),"Question/answer missing");
        h.assertTrue(compact.stream().allMatch(m->m.get("content").getAsString().length()<=180),"Unbounded history");
        h.assertTrue(history.getLast().get("content").getAsString().length()==2000,"Original history mutated"); h.succeed();
    }
    @GameTest(template="tall_tree",timeoutTicks=300)
    public static void realWorkFocusesWithoutAnnouncingSuccess(GameTestHelper h) {
        var c=fixture(h); c.setItem(0,new ItemStack(Items.OAK_PLANKS,4));
        c.setItem(1,new ItemStack(Items.STICK,2)); h.setBlock(new BlockPos(24,1,16),Blocks.CRAFTING_TABLE);
        var player=h.makeMockServerPlayerInLevel(); player.teleportTo(c.getX()-3,c.getY(),c.getZ()-3); c.setOwnerId(player.getUUID());
        var accepted=c.startWork("prepare_craft","minecraft:wooden_pickaxe",1);
        h.assertTrue(accepted.status()==CompanionActionResult.Status.ACCEPTED,"Work not accepted: "+accepted);
        h.assertTrue(c.expression()!=CompanionExpression.PLEASED,"Acceptance is not success");
        h.runAtTickTime(1,()->h.assertTrue(c.expression()==CompanionExpression.FOCUSED,"Real work has no focus: "+c.expression()));
        h.succeedWhen(()->{
            h.assertTrue(!c.isWorking()&&c.getMainHandItem().is(Items.WOODEN_PICKAXE),"Craft not finished: "+c.getLastTaskResult());
            h.assertTrue(c.expression()==CompanionExpression.PLEASED,"Actual craft has no pleased reaction: "+c.expression());
        });
    }
    @GameTest(template="tall_tree",timeoutTicks=180)
    public static void realResultReactionsReturnToCalm(GameTestHelper h) {
        var c=fixture(h); c.recordActionResult(CompanionActionResult.ok("Сделал нужную вещь."));
        h.runAtTickTime(2,()->h.assertTrue(c.expression()==CompanionExpression.PLEASED,"Success not shown"));
        h.runAtTickTime(110,()->{
            h.assertTrue(c.expression()==CompanionExpression.CALM,"Success stuck");
            c.recordActionResult(CompanionActionResult.blocked("Проход завален, не добрался."));
        });
        h.runAtTickTime(112,()->{h.assertTrue(c.expression()==CompanionExpression.WORRIED,"Failure not shown");h.succeed();});
    }
    @GameTest(template="tall_tree",timeoutTicks=200)
    public static void actualCombatAndLowHealthOverrideSpeech(GameTestHelper h) {
        var c=fixture(h); var zombie=h.spawn(EntityType.ZOMBIE,22,1,16); zombie.setNoAi(true); zombie.setInvulnerable(true);
        c.setTarget(zombie); c.reactToSpeech("Хех, шучу.");
        h.runAtTickTime(2,()->{h.assertTrue(c.expression()==CompanionExpression.COMBAT&&!c.sociallySafe()&&c.fighting(),"Fight priority"); c.setHealth(6);});
        h.runAtTickTime(4,()->{h.assertTrue(c.expression()==CompanionExpression.WORRIED,"Low health priority"); c.setHealth(20); c.setTarget(null); zombie.discard();});
        h.runAtTickTime(160,()->{h.assertTrue(c.expression()==CompanionExpression.CALM&&!c.fighting(),"Fight/danger stuck"); h.succeed();});
    }
    @GameTest(template="tall_tree",timeoutTicks=50)
    public static void trackingSnapshotAndWorldReloadShowCurrentFace(GameTestHelper h) {
        var c=fixture(h); c.setHealth(6); c.setItem(0,new ItemStack(Items.DIAMOND,3));
        h.runAtTickTime(2,()->{
            var tracking=AICompanionMod.COMPANION.get().create(h.getLevel());
            tracking.getEntityData().assignValues(c.getEntityData().getNonDefaultValues());
            h.assertTrue(tracking.expression()==CompanionExpression.WORRIED,"New client tracking lost expression");
            var saved=new CompoundTag(); c.addAdditionalSaveData(saved);
            var loaded=AICompanionMod.COMPANION.get().create(h.getLevel()); loaded.setHealth(6); loaded.readAdditionalSaveData(saved);
            h.assertTrue(loaded.expression()==CompanionExpression.WORRIED&&loaded.getItem(0).is(Items.DIAMOND)&&loaded.getItem(0).getCount()==3,"Reload face/items");
            saved.remove("SocialCadence"); saved.putFloat("Health",20); var legacy=AICompanionMod.COMPANION.get().create(h.getLevel()); legacy.readAdditionalSaveData(saved);
            h.assertTrue(legacy.expression()==CompanionExpression.CALM&&legacy.getItem(0).getCount()==3,"Legacy data unsafe");
            saved.putBoolean("GatheringLogs",true); saved.putInt("LogsRemaining",2);
            var working=AICompanionMod.COMPANION.get().create(h.getLevel());working.readAdditionalSaveData(saved);
            h.assertTrue(working.expression()==CompanionExpression.FOCUSED&&working.getItem(0).getCount()==3,"Saved work lost focus or items");h.succeed();
        });
    }
    @GameTest(template="tall_tree",timeoutTicks=280)
    public static void observationsStartQuietlyAndNeverSpeakInDanger(GameTestHelper h) {
        var c=fixture(h);
        for(int i=0;i<20;i++) c.observeEvent("early:"+i,"Блок реально сломан.");
        h.assertTrue(c.socialSequence()==0,"Immediate chatter on spawn");
        h.runAtTickTime(220,()->{
            mature(c,h); c.setHealth(6);
        });
        h.runAtTickTime(222,()->{
            for(int i=0;i<100;i++) c.observeEvent("danger:"+i,"Рядом опасность.");
            h.assertTrue(c.socialSequence()==0,"Danger triggers paid chatter");
            c.setHealth(20); c.setLifestyle(false,false);
        });
        h.runAtTickTime(270,()->{for(int i=0;i<100;i++) c.observeEvent("disabled:"+i,"Итог дела."); h.assertTrue(c.socialSequence()==0,"Disable ignored");h.succeed();});
    }
    @GameTest(template="tall_tree",timeoutTicks=250)
    public static void observationCooldownAndTopicsSurviveWorldSaving(GameTestHelper h) {
        var c=fixture(h);
        h.runAtTickTime(220,()->{
            mature(c,h);
            seedObservation(c); c.observeEvent("finished:iron","Добыл настоящую железную руду.");
            h.assertTrue(c.socialSequence()==1,"Mature observation not emitted");
            var saved=new CompoundTag(); c.addAdditionalSaveData(saved); long now=h.getLevel().getGameTime();
            long gap=saved.getCompound("SocialCadence").getLong("Next")-now;
            h.assertTrue(gap>=1200&&gap<=2400,"Server interval not one to two minutes: "+gap);
            var loaded=AICompanionMod.COMPANION.get().create(h.getLevel()); loaded.tickCount=300; loaded.readAdditionalSaveData(saved);
            for(int i=0;i<50;i++) loaded.observeEvent("other:"+i,"Ещё один блок.");
            h.assertTrue(loaded.socialSequence()==0,"Cooldown lost on reload");
            saved.getCompound("SocialCadence").putLong("Next",now);loaded.readAdditionalSaveData(saved);
            seedObservation(loaded);loaded.observeEvent("finished:iron","Та же самая находка.");
            h.assertTrue(loaded.socialSequence()==0,"Repeated topic after reload");
            seedObservation(loaded);loaded.observeEvent("weather:new","Начался дождь.");
            h.assertTrue(loaded.socialSequence()==1,"Different actual event not admitted");h.succeed();
        });
    }
    @GameTest(template="tall_tree",timeoutTicks=250)
    public static void aThreatAtThePlayerBlocksChatterEvenAwayFromOleg(GameTestHelper h) {
        var c=fixture(h); var stand=h.absolutePos(new BlockPos(8,1,16));c.moveTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5);
        var player=h.makeMockServerPlayerInLevel();var far=h.absolutePos(new BlockPos(28,1,16));player.teleportTo(far.getX()+.5,far.getY(),far.getZ()+.5);c.setOwnerId(player.getUUID());
        var zombie=h.spawn(EntityType.ZOMBIE,29,1,16);zombie.setNoAi(true);zombie.setInvulnerable(true);
        h.runAtTickTime(220,()->{
            mature(c,h);
            h.assertTrue(c.getTarget()==null,"Oleg already fighting, not testing distant owner threat");
            h.assertTrue(!c.sociallySafe()&&c.fighting(),"Threat near the owner ignored outside Oleg's twelve blocks");
            for(int i=0;i<50;i++) c.observeEvent("unsafe-owner:"+i,"Погода изменилась.");
            h.assertTrue(c.socialSequence()==0,"Chatter while the player is threatened");h.succeed();
        });
    }
    @GameTest(template="empty")
    public static void sixAtlasesKeepDimensionsBodyHairAndAlphaExactly(GameTestHelper h) throws Exception {
        var root="/assets/aicompanion/textures/entity/";
        var base=javax.imageio.ImageIO.read(CompanionCharacterTests.class.getResourceAsStream(root+"oleg.png"));
        for(var expression:CompanionExpression.values()) {
            var image=javax.imageio.ImageIO.read(CompanionCharacterTests.class.getResourceAsStream(root+expression.texture()+".png"));
            if(image.getWidth()!=1254||image.getHeight()!=1254) throw new AssertionError("Atlas dimensions changed");
            int changes=0;
            for(int y=0;y<1254;y++) for(int x=0;x<1254;x++) {
                int a=base.getRGB(x,y),b=image.getRGB(x,y);
                if((a>>>24)!=(b>>>24)) throw new AssertionError("Alpha changed");
                if(a==b) continue;
                int u=x-206,v=y-190;
                boolean mask=(v>=25&&v<91&&(u>=7&&u<72||u>=88&&u<151))||(u>=27&&u<135&&v>=125&&v<163);
                if(!mask) throw new AssertionError("Non-face pixel changed: "+x+","+y);
                changes++;
            }
            if(expression!=CompanionExpression.CALM&&changes<500) throw new AssertionError("Expression indistinct");
        }
        h.succeed();
    }
    private static JsonObject message(String role,String text) { var m=new JsonObject();m.addProperty("role",role);m.addProperty("content",text);return m; }
    private static void seedObservation(CompanionEntity c) {
        for(long seed=0;seed<10000;seed++) {c.getRandom().setSeed(seed);if(c.getRandom().nextFloat()<.35F){c.getRandom().setSeed(seed);return;}}
        throw new AssertionError("No deterministic observation seed");
    }
    private static CompanionEntity fixture(GameTestHelper h) {
        for(int x=0;x<32;x++) for(int z=0;z<32;z++) h.setBlock(new BlockPos(x,0,z),Blocks.BEDROCK);
        var c=h.spawn(AICompanionMod.COMPANION.get(),16,1,16); c.setFollowing(false); c.setLifestyle(false,true); return c;
    }
    private static void mature(CompanionEntity c,GameTestHelper h) {
        var saved=new CompoundTag();c.addAdditionalSaveData(saved);var cadence=new CompoundTag();cadence.putLong("Next",h.getLevel().getGameTime());saved.put("SocialCadence",cadence);c.readAdditionalSaveData(saved);
    }
}

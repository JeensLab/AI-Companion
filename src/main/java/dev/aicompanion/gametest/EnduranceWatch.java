package dev.aicompanion.gametest;

import com.google.gson.GsonBuilder;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.*;

/** Measures useful changes, including walking/animation with no harvest. Movement/status alone never reset this clock. */
final class EnduranceWatch {
    private final MarathonArena a;
    private final String name;
    private final Set<Long> seen = new HashSet<>();
    private final List<Map<String,Object>> samples = new ArrayList<>();
    private final Map<String,Integer> blockStarts = new HashMap<>(), routeStarts = new HashMap<>();
    private record Swing(String block,long ticks) { }
    private final Map<String,Swing> swings=new HashMap<>();
    private final Map<String,Integer> taken = new TreeMap<>();
    private long workId, lastUseful, worstUseless;
    private String lastProgress="", lastRoute="";
    private net.minecraft.world.level.pathfinder.Path lastPath;
    int successes, failures, reloads, maxBlockStarts, maxRouteStarts, minimumY=Integer.MAX_VALUE;
    private final Set<BlockPos> containers = new HashSet<>();
    private int maxOccupied;
    int harvestedSoil;
    private int lastSoil;
    private int lastLogs,lastStone;
    private int harvestedLogs,harvestedStone;
    private final Map<String,Integer> initial;
    final Set<String> toolsSeen=new TreeSet<>();
    private final Set<UUID> agingDrops=new HashSet<>();
    private final Map<String,String> agingDropDetails=new TreeMap<>();
    int peakCoal;

    EnduranceWatch(MarathonArena a,String name){this.a=a;this.name=name;initial=ledger();lastUseful=a.h.getTick();MarathonTicks.each(a.h,this::tick);}

    private void tick(){
        long tick=a.h.getTick();minimumY=Math.min(minimumY,a.oleg.getBlockY());
        int soil=a.oleg.planProgress("gather_dirt");harvestedSoil+=Math.max(0,soil-lastSoil);lastSoil=soil;
        int logs=a.oleg.planProgress("gather_wood"),stone=a.oleg.planProgress("gather_stone");
        harvestedLogs+=Math.max(0,logs-lastLogs);harvestedStone+=Math.max(0,stone-lastStone);lastLogs=logs;lastStone=stone;
        // A work ID belongs to one loaded entity instance; examine the completed old ID before observing the next.
        if(workId!=0&&!seen.contains(workId)){
            var result=a.oleg.workResult(workId);
            if(result!=null){seen.add(workId);if(result.success())successes++;else failures++;}
        }
        long id=a.oleg.activeWorkId();
        if(id!=0&&id!=workId){workId=id;blockStarts.clear();routeStarts.clear();swings.clear();lastRoute="";lastPath=null;}
        try{
            Object work=CompanionEnduranceTests.field(a.oleg,"work");
            observeSwing("work",CompanionEnduranceTests.field(work,"breaking"));
            observeSwing("wood",CompanionEnduranceTests.field(a.oleg,"woodBreaking"));
            observeSwing("dirt",CompanionEnduranceTests.field(a.oleg,"dirtBreaking"));
            if(tick%10!=0)return;
            var path=a.oleg.getNavigation().getPath();
            String route=path==null||a.oleg.getNavigation().isDone()?"":path.getTarget().toString();
            if(!route.isEmpty()&&(path!=lastPath||!route.equals(lastRoute)))maxRouteStarts=Math.max(maxRouteStarts,routeStarts.merge(route,1,Integer::sum));
            lastRoute=route;lastPath=path;
        }catch(Exception e){throw new IllegalStateException(e);}
        // Pickups, crafts, actual harvest and completed deliveries count; tool swaps, damage, text and walking do not.
        var bag=bag();toolsSeen.addAll(bag.keySet().stream().filter(s->s.endsWith("_pickaxe")||s.endsWith("_shovel")||s.endsWith("_axe")).toList());
        String progress=a.oleg.planProgress("gather_wood")+":"+a.oleg.planProgress("gather_stone")+":"+a.oleg.planProgress("gather_dirt")+":"+bag+":"+successes
                +(a.oleg.workPhase().endsWith("/COOK")?":"+ledger():"");
        if(!progress.equals(lastProgress)||!a.oleg.hasActiveWorkOrder()){lastProgress=progress;lastUseful=tick;}
        else worstUseless=Math.max(worstUseless,tick-lastUseful);
        if(tick%1200==0){
            peakCoal=Math.max(peakCoal,total(Items.COAL));
            var box=new net.minecraft.world.phys.AABB(a.h.absolutePos(a.cell(20,0,20)).getCenter(),a.h.absolutePos(a.cell(77,40,73)).getCenter());
            for(var item:a.h.getLevel().getEntitiesOfClass(ItemEntity.class,box))
                if(item.getAge()>=20*240&&item.getPersistentData().hasUUID("aicompanion:harvester")&&a.oleg.getUUID().equals(item.getPersistentData().getUUID("aicompanion:harvester"))){agingDrops.add(item.getUUID());agingDropDetails.putIfAbsent(item.getUUID().toString(),BuiltInRegistries.ITEM.getKey(item.getItem().getItem())+"x"+item.getItem().getCount()+" at "+item.blockPosition().toShortString()+" minute "+tick/1200.0+"; Oleg "+a.oleg.blockPosition().toShortString()+" "+a.oleg.workPhase()+" "+a.oleg.getLastTaskResult());}
            var sample=new LinkedHashMap<String,Object>();sample.put("gameMinute",tick/1200.0);sample.put("successes",successes);sample.put("failures",failures);
            sample.put("harvestedLogs",harvestedLogs);sample.put("harvestedStone",harvestedStone);sample.put("harvestedSoil",harvestedSoil);
            sample.put("physicalItems",ledger());sample.put("usedByPlayer",new TreeMap<>(taken));sample.put("phase",a.oleg.workPhase());sample.put("position",a.watch.where(a.oleg.blockPosition()));
            sample.put("worstActiveSecondsWithoutUsefulChange",worstUseless/20.0);samples.add(sample);
            maxOccupied=Math.max(maxOccupied,occupied());dump();
        }
    }

    private void observeSwing(String kind,Object breaking)throws Exception {
        Object target=CompanionEnduranceTests.field(breaking,"target");long ticks=((Number)CompanionEnduranceTests.field(breaking,"ticks")).longValue();
        String block=target==null?"":target.toString();Swing old=swings.put(kind,new Swing(block,ticks));
        if(!block.isEmpty()&&(old==null||!block.equals(old.block())||ticks<old.ticks()))maxBlockStarts=Math.max(maxBlockStarts,blockStarts.merge(kind+":"+block,1,Integer::sum));
    }

    private Map<String,Integer> bag(){
        var result=new TreeMap<String,Integer>();
        for(int i=0;i<a.oleg.getContainerSize();i++)add(result,a.oleg.getItem(i));
        for(var slot:EquipmentSlot.values())add(result,a.oleg.getItemBySlot(slot));
        return result;
    }
    private static void add(Map<String,Integer> result,ItemStack stack){if(!stack.isEmpty())result.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),Integer::sum);}
    private void discoverContainers(){
        try {
            var method=CompanionEntity.class.getDeclaredMethod("placedByMe",String.class);method.setAccessible(true);
            for(String kind:List.of("minecraft:chest","minecraft:furnace"))
                for(Object pos:(java.util.List<?>)method.invoke(a.oleg,kind))containers.add((BlockPos)pos);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    Map<String,Integer> ledger(){
        discoverContainers();var result=bag();
        for(BlockPos pos:containers)if(a.h.getLevel().getBlockEntity(pos) instanceof Container c)
            for(int i=0;i<c.getContainerSize();i++)add(result,c.getItem(i));
        var box=new net.minecraft.world.phys.AABB(a.h.absolutePos(a.cell(20,0,20)).getCenter(),a.h.absolutePos(a.cell(77,40,73)).getCenter());
        for(var item:a.h.getLevel().getEntitiesOfClass(ItemEntity.class,box))add(result,item.getItem());
        return result;
    }
    int total(Item item){return ledger().getOrDefault(BuiltInRegistries.ITEM.getKey(item).toString(),0);}
    int torches(){int n=0;for(BlockPos pos:BlockPos.betweenClosed(a.h.absolutePos(a.cell(20,9,20)),a.h.absolutePos(a.cell(76,18,72))))if(a.h.getLevel().getBlockState(pos).is(Blocks.TORCH))n++;return n;}
    private int occupied(){int n=0;for(int i=0;i<a.oleg.getContainerSize();i++)if(!a.oleg.getItem(i).isEmpty())n++;return n;}

    void takeStored(Item item,int wanted){
        discoverContainers();int removed=0;
        for(BlockPos pos:containers)if(a.h.getLevel().getBlockEntity(pos) instanceof ChestBlockEntity c){
            for(int i=0;i<c.getContainerSize()&&removed<wanted;i++)if(c.getItem(i).is(item)){
                var stack=c.removeItem(i,wanted-removed);removed+=stack.getCount();a.player.getInventory().add(stack);
            }
        }
        taken.merge(BuiltInRegistries.ITEM.getKey(item).toString(),removed,Integer::sum);
        a.watch.note("Player used own stored "+item+" x"+removed);
    }

    void reload(Runnable after){
        try{
            Map<String,Integer> before=ledger();var uuid=a.oleg.getUUID();var area=a.oleg.autonomy().workArea();
            List<ItemStack> stacks=new ArrayList<>();for(int i=0;i<a.oleg.getContainerSize();i++)stacks.add(a.oleg.getItem(i).copy());
            List<ItemStack> equipment=new ArrayList<>();for(var slot:EquipmentSlot.values())equipment.add(a.oleg.getItemBySlot(slot).copy());
            var tag=new CompoundTag();a.oleg.saveWithoutId(tag);
            var dir=java.nio.file.Path.of("endurance");java.nio.file.Files.createDirectories(dir);var path=dir.resolve(name+"-entity.nbt");
            NbtIo.writeCompressed(tag,path);CompoundTag disk=NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap());
            boolean active=tag.getBoolean("WorkActive");var old=a.oleg;a.oleg.discard();a.chat.worldClosed();
            a.h.runAfterDelay(1,()->{
                var next=AICompanionMod.COMPANION.get().create(a.h.getLevel());next.load(disk);
                a.h.assertTrue(a.h.getLevel().addFreshEntity(next),"Saved entity rejected");
                a.oleg=next;a.watch.watch(next);a.chat.watch(next);workId=0;seen.clear();lastSoil=lastLogs=lastStone=0;lastUseful=a.h.getTick();reloads++;
                a.expect(old.isRemoved()&&uuid.equals(next.getUUID()),"Old entity survives / UUID changed");
                a.expect(Objects.equals(area,next.autonomy().workArea()),"Workplace memory lost");
                a.expect(before.equals(ledger()),"Items changed across disk roundtrip: "+before+" -> "+ledger());
                for(int i=0;i<stacks.size();i++)a.expect(ItemStack.matches(stacks.get(i),next.getItem(i)),"Slot/components changed: "+i);
                int i=0;for(var slot:EquipmentSlot.values())a.expect(ItemStack.matches(equipment.get(i++),next.getItemBySlot(slot)),"Equipment/components changed: "+slot);
                if(active){String result=next.getLastTaskResult();a.expect(!next.didLastTaskSucceed()&&(result.contains("прервалась")||result.contains("прервана")),"Reload claims completion of interrupted work: success="+next.didLastTaskSucceed()+", result="+result);}
                unique();after.run();
            });
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    void unique(){int n=0;for(var entity:a.h.getLevel().getAllEntities())if(entity instanceof CompanionEntity c&&a.player.getUUID().equals(c.getOwnerId()))n++;a.expect(n==1,"Companion count="+n);a.expect(CompanionEvents.getOwnedCompanion(a.player)==a.oleg,"Canonical companion differs");}
    void verify(){
        a.watch.assertAlive();a.watch.assertNoTeleport();unique();
        a.expect(returnPathExists(),"No walkable return to the saved workplace height at session end");
        a.expect(worstUseless<=20*95,"Busy without useful change for "+worstUseless/20+"s");
        a.expect(maxBlockStarts<=6,"Repeated same block within a job: "+maxBlockStarts);
        a.expect(agingDrops.isEmpty(),"Own harvest lay uncollected until close to despawning: "+agingDropDetails);
        a.watch.assertNothingLeft();
        // Route starts are measured and reported, not confused with movement progress or an arbitrary failure threshold.
        dump();
    }
    void returnSnapshot(){
        try{
            BlockPos c=a.oleg.autonomy().workArea(),from=a.oleg.blockPosition();
            var report=new LinkedHashMap<String,Object>();report.put("from",from.toShortString());report.put("surface",c.toShortString());report.put("vanillaPath",returnPathExists());
            report.put("caller",a.player.blockPosition().toShortString());var called=a.oleg.getNavigation().createPath(a.player.blockPosition(),1);report.put("pathToCaller",called!=null&&called.canReach());
            var type=Class.forName("dev.aicompanion.entity.CompanionMiningNavigation");var back=type.getDeclaredMethod("canWalkBackUp",CompanionEntity.class,BlockPos.class,int.class);back.setAccessible(true);
            report.put("safetySearch",back.invoke(null,a.oleg,from,c.getY()));
            var layers=new LinkedHashMap<String,Object>();
            for(int y=c.getY()+3;y>=c.getY()-14;y--){var rows=new ArrayList<String>();for(int z=c.getZ()-24;z<=c.getZ()+24;z++){var row=new StringBuilder();for(int x=c.getX()-24;x<=c.getX()+24;x++){
                BlockPos p=new BlockPos(x,y,z);var s=a.h.getLevel().getBlockState(p);row.append(p.equals(from)?'O':s.isAir()?'.':s.is(Blocks.STONE)?'#':s.is(Blocks.DIRT)?'d':s.is(Blocks.BEDROCK)?'B':s.is(Blocks.CHEST)?'C':s.is(Blocks.CRAFTING_TABLE)?'T':s.is(Blocks.FURNACE)?'F':'+');
            }rows.add(row.toString());}layers.put(Integer.toString(y-c.getY()),rows);}
            report.put("layers",layers);var dir=java.nio.file.Path.of("endurance");java.nio.file.Files.createDirectories(dir);java.nio.file.Files.writeString(dir.resolve(name+"-return.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private boolean returnPathExists(){
        if(a.oleg.autonomy().workArea()==null)return true;
        BlockPos center=a.oleg.autonomy().workArea();if(a.oleg.getY()>=center.getY()-.5)return true;
        var exits=new HashSet<BlockPos>();
        try{var type=Class.forName("dev.aicompanion.entity.CompanionMiningNavigation");var stand=type.getDeclaredMethod("standable",CompanionEntity.class,BlockPos.class);stand.setAccessible(true);
            for(BlockPos pos:BlockPos.betweenClosed(center.offset(-24,0,-24),center.offset(24,1,24)))if((boolean)stand.invoke(null,a.oleg,pos))exits.add(pos.immutable());
        }catch(Exception e){throw new IllegalStateException(e);}
        var route=exits.isEmpty()?null:a.oleg.getNavigation().createPath(exits,0);
        return route!=null&&route.canReach();
    }
    String summary(){return "successes="+successes+", failures="+failures+", reloads="+reloads+", harvestedLogs="+harvestedLogs+", harvestedStone="+harvestedStone+", harvestedSoil="+harvestedSoil+", worstUsefulIdleSeconds="+worstUseless/20+", maxBlockStarts="+maxBlockStarts+", maxRouteStarts="+maxRouteStarts+", minimumY="+minimumY+", maxOccupiedBagSlots="+maxOccupied+", physicalItems="+ledger()+", playerUsed="+taken;}
    private void dump(){try{var dir=java.nio.file.Path.of("endurance");java.nio.file.Files.createDirectories(dir);var report=new LinkedHashMap<String,Object>();report.put("scenario",name);report.put("gameTicks",a.h.getTick());report.put("summary",summary());report.put("initialPhysicalItems",initial);report.put("toolsObserved",toolsSeen);report.put("worstActiveSecondsWithoutUsefulChange",worstUseless/20.0);report.put("maxBlockStarts",maxBlockStarts);report.put("maxRouteStarts",maxRouteStarts);report.put("harvestedLogs",harvestedLogs);report.put("harvestedStone",harvestedStone);report.put("harvestedSoil",harvestedSoil);report.put("successes",successes);report.put("failures",failures);report.put("reloads",reloads);report.put("agingOwnDrops",agingDrops.size());var current=ledger();report.put("logsConvertedOrUsed",initial.getOrDefault("minecraft:oak_log",0)+harvestedLogs-current.getOrDefault("minecraft:oak_log",0)-taken.getOrDefault("minecraft:oak_log",0));report.put("stoneUsedForRecipesOrPlacement",initial.getOrDefault("minecraft:cobblestone",0)+harvestedStone-current.getOrDefault("minecraft:cobblestone",0)-taken.getOrDefault("minecraft:cobblestone",0));report.put("samples",samples);java.nio.file.Files.writeString(dir.resolve(name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(java.io.IOException e){throw new IllegalStateException(e);}}
}

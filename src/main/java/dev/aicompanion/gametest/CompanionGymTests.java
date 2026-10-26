package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

/** Deterministic player-simulator workflows. No model, voice API or paid requests. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGymTests {
    @GameTest(template = "tall_tree", timeoutTicks = 2600)
    public static void dummyPlayerRequestsCraftMineStoreRetrieveEquipAndReturn(GameTestHelper h) {
        var c=setup(h); var p=h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        c.setItem(0,new ItemStack(Items.OAK_LOG,8));
        for(int z=15;z<=20;z++) h.setBlock(new BlockPos(20,1,z),Blocks.STONE);
        var plan=List.of(new PlannedAction("craft_item",0,1,"minecraft:wooden_pickaxe"),
                new PlannedAction("gather_stone",0,3),new PlannedAction("craft_item",0,1,"minecraft:stone_axe"),
                new PlannedAction("place_block",0,1,"minecraft:chest"),new PlannedAction("store_items",0,1,"resources"),
                new PlannedAction("take_items",0,1,"all"),new PlannedAction("equip_item",0,1,"minecraft:stone_axe","mainhand"),
                new PlannedAction("come",0,1));
        int[] step={0}; long[] work={0};
        h.onEachTick(() -> {
            if(step[0]>=plan.size()) return;
            if(work[0]>0) {
                var result=c.workResult(work[0]); if(result==null) return;
                h.assertTrue(result.success() && result.status()==CompanionActionResult.Status.SUCCESS,"Step "+step[0]+": "+result);
                step[0]++; work[0]=0; return;
            }
            if(c.isGatheringTask() || c.isWorking() || !c.onGround()) return;
            var action=plan.get(step[0]);
            if(action.type().equals("come")) { BlockPos dest=h.absolutePos(new BlockPos(8,1,8)); p.teleportTo(dest.getX()+.5,dest.getY(),dest.getZ()+.5); }
            var result=CompanionEvents.executeAction(p,action.type(),action.item(),action.count(),action.slot());
            h.assertTrue(result.success(),"Dispatch "+step[0]+": "+result);
            work[0]=c.activeWorkId(); if(work[0]==0) step[0]++;
        });
        h.succeedWhen(() -> {
            h.assertTrue(step[0]==plan.size(),"Workflow waiting at "+step[0]+": "+c.getLastTaskResult());
            h.assertTrue(c.getMainHandItem().is(Items.STONE_AXE) && c.distanceToSqr(p)<10,"Equipment or return postcondition failed");
        });
    }
    @GameTest(template = "tall_tree", timeoutTicks = 160)
    public static void visibleAttackerTriggersDefenseWithoutModelOrGuardOrder(GameTestHelper h) {
        var c=setup(h); var p=h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId()); c.setFollowing(false); c.setItem(0,new ItemStack(Items.WOODEN_SWORD));
        var zombie=h.spawn(EntityType.ZOMBIE,19,1,16); zombie.setNoAi(true);
        p.setLastHurtByMob(zombie);
        h.succeedWhen(() -> h.assertTrue(c.getTarget()==zombie && c.getMainHandItem().is(Items.WOODEN_SWORD),"Owner defense reflex did not take control"));
    }
    private static CompanionEntity setup(GameTestHelper h) {
        for(int x=0;x<32;x++) for(int z=0;z<32;z++) h.setBlock(new BlockPos(x,0,z),Blocks.BEDROCK);
        var c=h.spawn(AICompanionMod.COMPANION.get(),16,1,16); var p=h.makeMockServerPlayerInLevel();
        p.teleportTo(c.getX()-4,c.getY(),c.getZ()-4); c.setOwnerId(p.getUUID());
        p.getPersistentData().putUUID("aicompanion:canonical_companion",c.getUUID()); CompanionEvents.getOwnedCompanion(p); c.setSearchRadius(16); return c;
    }
}

package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Session e7fd0ff7: after a pickup the player approaches the next stair and the same-cell retreat loops. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionStairApproachTests {
    @GameTest(template = "tall_tree", timeoutTicks = 2600)
    public static void playerStepsOntoPlannedStairsHeFindsAnotherDescent(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 5; y++)
            h.setBlock(new BlockPos(x,y,z), y == 0 ? Blocks.BEDROCK : y < 3 ? Blocks.STONE : Blocks.DIRT);
        CompanionEntity oleg = h.spawn(AICompanionMod.COMPANION.get(), 16,6,16);
        oleg.setFollowing(false);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(oleg.getX()+6,oleg.getY(),oleg.getZ()+6);
        oleg.setOwnerId(player.getUUID());
        oleg.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE));
        BlockPos[] cells = new BlockPos[2];
        boolean[] entered = {false};
        h.onEachTick(() -> {
            if (cells[0] == null) {
                if (!oleg.onGround()) return;
                var plan = CompanionQuarry.find(oleg,8);
                h.assertTrue(plan != null && plan.cells().size() > 3,"No staircase fixture at " + oleg.blockPosition());
                cells[0] = plan.cells().getFirst(); cells[1] = plan.cells().get(3);
                h.assertTrue(oleg.startWork("gather_stone","",8).success(),"Order refused");
            }
            BlockPos blocked = cells[1];
            if (!entered[0] && h.getLevel().getBlockState(cells[0]).isAir()) {
                entered[0] = true;
                player.setNoGravity(true);
                player.teleportTo(blocked.getX()+.5,blocked.getY()+1,blocked.getZ()+.5);
                dev.aicompanion.CompanionDiagnostics.event("", "test_stair_occupied", "entity", oleg.getUUID(), "blocked", blocked);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(entered[0] && !oleg.hasActiveWorkOrder(),"Still working: "+oleg.getLastTaskResult());
            h.assertTrue(oleg.didLastTaskSucceed() && oleg.collectedStone() >= 8,"No actual stone: "+oleg.getLastTaskResult());
            h.assertTrue(!h.getLevel().getBlockState(cells[1]).isAir(),"Mined under the player");
            oleg.setLifestyle(false, false); CompanionChunks.release(oleg.getUUID());
        });
    }

}

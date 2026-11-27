package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionPet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionParrotTamingTests {
    @GameTest(template = "large_search", skyAccess = true, timeoutTicks = 650, batch = "parrot_alternative")
    public static void picksUpSeedsAndLuresAnotherBirdDownWhenTheNearestIsEnclosed(GameTestHelper h) {
        var oleg = CompanionPetSearchTests.fixture(h, false, false);
        oleg.setPetSearchRadius(32);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(oleg.getOwnerId());
        for (int x = 11; x <= 13; x++) for (int z = 7; z <= 9; z++) for (int y = 3; y <= 5; y++) {
            if (x != 12 || z != 8 || y == 5) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
        var caged = h.spawn(EntityType.PARROT, 12, 3, 8);
        caged.setNoAi(true);
        // The perch is beyond lure range from the loose seeds, too high to walk onto.
        for (int y = 3; y <= 8; y++) h.setBlock(new BlockPos(22, y, 17), Blocks.JUNGLE_LOG);
        var free = h.spawn(EntityType.PARROT, 22, 9, 17);
        // Perch here until offered food, then use the real flying navigation to approach Oleg.
        free.goalSelector.removeAllGoals(goal -> true);
        double perchY = free.getY();

        var pos = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6, 3, 13)));
        var seeds = new ItemEntity(h.getLevel(), pos.x, pos.y, pos.z, new ItemStack(Items.MELON_SEEDS, 5));
        seeds.setDeltaMovement(0, 0, 0);
        h.assertTrue(h.getLevel().addFreshEntity(seeds), "Could not place loose seeds in the world");
        var result = CompanionEvents.executeAction(player, "tame_parrot", "", 1);
        long order = oleg.activeWorkId();
        h.assertTrue(result.success() && order > 0, "Could not start the parrot search: " + result.message());
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(oleg.petQuestion(CompanionPet.PARROT) == order, "Did not ask for seeds"))
                .thenExecute(() -> CompanionEvents.answerPetQuestion(player, oleg.getUUID(), CompanionPet.PARROT, order, "no"))
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(oleg.awaitsPetSearchPermission() && seeds.isAlive() && seedCount(oleg) == 0,
                            "Collected seeds before permission");
                    CompanionEvents.answerPetQuestion(player, oleg.getUUID(), CompanionPet.PARROT, order, "search");
                })
                .thenWaitUntil(() -> h.assertTrue(!seeds.isAlive() && seedCount(oleg) == 5, "Did not collect the real melon seeds"))
                .thenWaitUntil(() -> h.assertTrue(seedCount(oleg) == 4, "Did not offer a seed to the reachable parrot: " + oleg.getLastTaskResult()
                        + "; Oleg at " + oleg.blockPosition().subtract(h.absolutePos(BlockPos.ZERO))
                        + ", parrot at " + free.blockPosition().subtract(h.absolutePos(BlockPos.ZERO))
                        + ", visible=" + oleg.hasLineOfSight(free) + ", alive=" + free.isAlive()))
                .thenExecute(() -> {
                    h.assertTrue(!caged.isTame() && !oleg.hasLineOfSight(caged), "Fed the nearest bird through a wall");
                    h.assertTrue(free.getY() < perchY - 1 && free.distanceToSqr(oleg) < 5, "Did not lure the reachable bird down");
                }).thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 120, batch = "parrot_lost_target")
    public static void keepsEverySeedIfTheOnlyWildBirdLeavesBeforeFeeding(GameTestHelper h) {
        var oleg = CompanionPetSearchTests.fixture(h, false, false);
        oleg.setPetSearchRadius(32);
        var bird = h.spawn(EntityType.PARROT, 11, 3, 8);
        oleg.setItem(0, new ItemStack(Items.BEETROOT_SEEDS, 3));
        var player = h.getLevel().getServer().getPlayerList().getPlayer(oleg.getOwnerId());
        var result = CompanionEvents.executeAction(player, "tame_parrot", "", 1);
        h.assertTrue(result.success() && oleg.hasActiveWorkOrder(), "Did not start a real taming order");
        bird.discard();
        h.succeedWhen(() -> {
            h.assertTrue(!oleg.hasActiveWorkOrder(), "The order still targets the missing parrot");
            h.assertTrue(seedCount(oleg) == 3 && !oleg.hasPet(CompanionPet.PARROT), "Spent seeds or invented a pet");
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 100, batch = "parrot_offhand")
    public static void offersSeedsFromHisOffhandWithoutPuttingAwayHisTool(GameTestHelper h) {
        var oleg = CompanionPetSearchTests.fixture(h, false, false);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(oleg.getOwnerId());
        var bird = h.spawn(EntityType.PARROT, 9, 3, 8);
        bird.setNoAi(true);
        oleg.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        oleg.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WHEAT_SEEDS, 3));
        CompanionEvents.executeAction(player, "tame_parrot", "", 1);
        h.succeedWhen(() -> {
            h.assertTrue(oleg.getOffhandItem().getCount() == 2, "Did not feed a seed from his offhand");
            h.assertTrue(oleg.getMainHandItem().is(Items.IRON_PICKAXE), "Replaced the tool while his other hand held seeds");
        });
    }

    private static int seedCount(CompanionEntity oleg) {
        int count = oleg.getMainHandItem().is(ItemTags.PARROT_FOOD) ? oleg.getMainHandItem().getCount() : 0;
        if (oleg.getOffhandItem().is(ItemTags.PARROT_FOOD)) count += oleg.getOffhandItem().getCount();
        for (int slot = 0; slot < oleg.getContainerSize(); slot++) {
            var stack = oleg.getItem(slot);
            if (stack.is(ItemTags.PARROT_FOOD)) count += stack.getCount();
        }
        return count;
    }
}

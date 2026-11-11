package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The player's things he gives back are flagged as a gift, so his loose pickup never takes them again. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPlayersThingsGiftTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void thePlayersThingsGivenBackAreFlaggedAsAGift(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 8);
        var player = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(9, 1, 8));
        player.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setFollowing(false);
        companion.playersThings.keep(new ItemStack(Items.IRON_INGOT, 4));
        h.succeedWhen(() -> {
            var tossed = h.getLevel().getEntitiesOfClass(ItemEntity.class, companion.getBoundingBox().inflate(6.0D),
                    e -> e.getItem().is(Items.IRON_INGOT));
            h.assertTrue(!tossed.isEmpty(), "The player's things were not given back");
            for (ItemEntity item : tossed)
                h.assertTrue(item.getPersistentData().getBoolean("aicompanion:gift"), "The thing given back is not flagged as a gift");
        });
    }
}

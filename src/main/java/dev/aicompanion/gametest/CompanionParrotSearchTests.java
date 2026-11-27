package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionPet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionParrotSearchTests {
    @GameTest(template = "large_search", skyAccess = true, timeoutTicks = 700, batch = "parrot_jungle_trip")
    public static void asksBeforeWalkingAndSpotsAParrotOnArrivalInTheJungle(GameTestHelper h) {
        var companion = CompanionPetSearchTests.fixture(h, false, false);
        companion.setPetSearchRadius(32);
        var player = owner(h, companion);
        BlockPos start = companion.blockPosition();
        for (int x = 20; x <= 45; x++) for (int z = 0; z <= 20; z++) h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        paintBiome(h, start.offset(-6, -6, -8), start.offset(11, 7, 8), Biomes.PLAINS);
        paintBiome(h, start.offset(12, -6, -8), start.offset(38, 7, 8), Biomes.BAMBOO_JUNGLE);
        h.onEachTick(() -> player.teleportTo(companion.getX() - 2, companion.getY(), companion.getZ()));
        var result = CompanionEvents.executeAction(player, "tame_parrot", "jungle:east", 1);
        h.assertTrue(result.success(), result.message());
        long id = companion.activeWorkId();
        h.startSequence().thenWaitUntil(() -> h.assertTrue(companion.petQuestion(CompanionPet.PARROT) == id
                        && companion.autonomyLine().contains("джунгли"), "Did not offer the jungle route"))
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(companion.blockPosition().equals(start), "Walked before the player agreed");
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.PARROT, id, "go");
                }).thenWaitUntil(() -> h.assertTrue(companion.petQuestionMode() == CompanionPet.Question.HABITAT_SEARCH
                        && companion.petQuestion(CompanionPet.PARROT) == id, "Did not arrive and resume the parrot order"))
                .thenExecute(() -> {
                    h.assertTrue(h.getLevel().getBiome(companion.blockPosition()).is(BiomeTags.IS_JUNGLE)
                            && companion.getBlockX() > start.getX() + 4, "Reported the jungle while still in the plains");
                    h.assertTrue(companion.autonomyLine().contains("попугая"), "Did not invite the player to look for the bird");
                    h.spawn(EntityType.PARROT, companion.blockPosition().east(2).subtract(h.absolutePos(BlockPos.ZERO))).setNoAi(true);
                }).thenWaitUntil(() -> h.assertTrue(companion.petQuestionMode() == CompanionPet.Question.BAIT
                        && companion.petQuestion(CompanionPet.PARROT) == id && companion.autonomyLine().contains("сам нашёл дикого попугая")
                        && companion.autonomyLine().contains("семена"),
                        "Did not report the wild parrot and ask for seeds: " + companion.autonomyLine()))
                .thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 400, batch = "parrot_chest_bait")
    public static void takesSeedsFromHisChestOnlyAfterPermission(GameTestHelper h) {
        var companion = CompanionPetSearchTests.fixture(h, false, false);
        companion.setPetSearchRadius(32);
        var player = owner(h, companion);
        h.spawn(EntityType.PARROT, 12, 3, 8).setNoAi(true);
        var pos = new BlockPos(10, 3, 6);
        h.setBlock(pos, Blocks.CHEST);
        var chest = (Container) h.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.COOKIE, 5));
        chest.setItem(1, new ItemStack(Items.PUMPKIN_SEEDS, 5));
        companion.rememberPlaced(h.absolutePos(pos));
        CompanionEvents.executeAction(player, "tame_parrot", "", 1);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(companion.petQuestion(CompanionPet.PARROT) > 0, "Did not ask for seeds"))
                .thenExecute(() -> CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.PARROT,
                        companion.petQuestion(CompanionPet.PARROT), "no"))
                .thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(companion.awaitsPetSearchPermission() && chest.getItem(1).getCount() == 5,
                            "Took the seeds before permission");
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.PARROT,
                            companion.petQuestion(CompanionPet.PARROT), "search");
                }).thenWaitUntil(() -> h.assertTrue(chest.getItem(1).getCount() == 2, "Did not collect the real pumpkin seeds"))
                .thenExecute(() -> h.assertTrue(chest.getItem(0).getCount() == 5, "Picked cookies for the parrot"))
                .thenSucceed();
    }

    private static void paintBiome(GameTestHelper h, BlockPos from, BlockPos to, ResourceKey<Biome> biome) {
        var level = h.getLevel();
        var holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);
        h.assertTrue(FillBiomeCommand.fill(level, from, to, holder).left().isPresent(), "Could not prepare the test biome");
    }

    private static ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
}

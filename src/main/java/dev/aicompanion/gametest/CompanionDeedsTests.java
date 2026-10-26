package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The journal of deeds: real outcomes in order, kept across leaving the world, so reasons come from facts. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionDeedsTests {
    /**
     * A craft and a stone order stopped by «стоп» are journaled oldest first with their outcome.
     * The journal survives saving; a companion saved before the journal existed loads with an empty one.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void realOutcomesAreJournaledAndSurviveSaving(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        h.assertTrue(CompanionEvents.executeAction(owner, "craft_item", "minecraft:stick", 1).success(), "Craft not accepted");
        int[] stage = {0};
        h.onEachTick(() -> {
            if (stage[0] == 0 && !companion.isWorking()) {
                stage[0] = 1;
                h.assertTrue(companion.startWork("gather_stone", "", 2).success(), "Stone order not accepted");
                h.assertTrue(CompanionEvents.executeAction(owner, "stop", "", 1).success(), "Stop not accepted");
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(stage[0] == 1 && !companion.isWorking(), "Still working: " + companion.getLastTaskResult());
            String deeds = companion.getDeedsForAi();
            int craft = deeds.indexOf("сделано — Скрафтил"), stone = deeds.indexOf("не вышло — Прервал: добычу камня");
            h.assertTrue(craft >= 0 && stone > craft && companion.deedCount() == 2, "Journal order or outcomes wrong: " + deeds);
            h.assertTrue(!deeds.contains("В сумке"), "Stale bag listing kept in the journal: " + deeds);
            h.assertTrue(deeds.startsWith("только что: "), "Time not in words: " + deeds);

            CompoundTag saved = new CompoundTag();
            companion.addAdditionalSaveData(saved);
            CompanionEntity reloaded = AICompanionMod.COMPANION.get().create(h.getLevel());
            reloaded.readAdditionalSaveData(saved);
            h.assertTrue(reloaded.getDeedsForAi().equals(deeds) && reloaded.deedCount() == 2, "Journal lost on reload: " + reloaded.getDeedsForAi());

            CompoundTag older = saved.copy(); older.remove("Deeds");
            CompanionEntity legacy = AICompanionMod.COMPANION.get().create(h.getLevel());
            legacy.readAdditionalSaveData(older);
            h.assertTrue(legacy.deedCount() == 0 && legacy.getDeedsForAi().equals("пока нет"), "Old save without a journal: " + legacy.getDeedsForAi());
        });
    }

    /** No stone anywhere: the journal keeps the real reason of the failure, not a guess. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void failureKeepsItsRealReason(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(companion.startWork("gather_stone", "", 2).success(), "Stone order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isWorking(), "Still working: " + companion.getLastTaskResult());
            String deeds = companion.getDeedsForAi();
            h.assertTrue(!companion.didLastTaskSucceed() && deeds.contains("не вышло — ") && deeds.contains("камня"), "No reason journaled: " + deeds);
        });
    }

    /** The journal is bounded: 24 kept, 8 shown, a repeat within half a minute is not a new deed. */
    @GameTest(template = "tall_tree")
    public static void journalIsBounded(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        for (int i = 1; i <= 30; i++) companion.recordActionResult(CompanionActionResult.ok("Дело номер " + i + "."));
        companion.recordActionResult(CompanionActionResult.ok("Дело номер 30."));
        companion.recordActionResult(CompanionActionResult.ok("x".repeat(400)));
        String deeds = companion.getDeedsForAi();
        h.assertTrue(companion.deedCount() == 24, "Not bounded: " + companion.deedCount());
        h.assertTrue(deeds.split("; ").length == 8 && deeds.contains("Дело номер 24.") && !deeds.contains("Дело номер 23."), "Wrong shown part: " + deeds);
        h.assertTrue(deeds.endsWith("…") && deeds.length() < 8 * 200, "Long text not cut: " + deeds.length());
        h.succeed();
    }

    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 2, companion.getY(), companion.getZ() - 2);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(16);
        return companion;
    }
}

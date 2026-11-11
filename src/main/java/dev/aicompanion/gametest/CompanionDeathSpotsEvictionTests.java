package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionDeathSpots;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Over the limit of remembered places the cheapest one is forgotten, not the oldest: the dearest one is what he walks back for. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionDeathSpotsEvictionTests {
    @GameTest(template = "empty")
    public static void theCheapestPlaceIsForgottenNotTheOldest(GameTestHelper h) {
        CompanionDeathSpots spots = new CompanionDeathSpots();
        UUID owner = UUID.randomUUID();
        // The first and oldest is the dearest; the fourth is the cheapest.
        double[] worth = {500, 40, 30, 5, 60, 70, 80, 90, 100};
        for (int i = 0; i < worth.length; i++) spots.died(owner, Level.OVERWORLD, new BlockPos(i, 64, 0), worth[i], i);
        var all = spots.all(owner);
        h.assertTrue(all.size() == 8, "Kept " + all.size());
        h.assertTrue(all.stream().anyMatch(spot -> spot.worth() == 500), "The dearest, oldest place was forgotten: " + all);
        h.assertTrue(all.stream().noneMatch(spot -> spot.worth() == 5), "The cheapest place was kept: " + all);
        // A new place cheaper than all the kept ones stays: his things lie there now.
        spots.died(owner, Level.OVERWORLD, new BlockPos(9, 64, 0), 1, 9);
        h.assertTrue(spots.all(owner).stream().anyMatch(spot -> spot.worth() == 1), "The newest place was forgotten: " + spots.all(owner));
        h.succeed();
    }
}

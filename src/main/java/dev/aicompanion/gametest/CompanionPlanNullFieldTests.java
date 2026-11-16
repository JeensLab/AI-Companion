package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A model that writes {@code "count": null} for «no number» must not make the whole plan invalid. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPlanNullFieldTests {
    @GameTest(template = "empty")
    public static void nullCountAndItemMeanMissing(GameTestHelper h) {
        var steps = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"gather_stone\",\"count\":null,\"item\":null}]"));
        h.assertTrue(steps.size() == 1 && steps.getFirst().count() == 8, "Parsed " + steps);
        boolean rejected;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":null,\"count\":1}]")); rejected = false; }
        catch (IllegalStateException invalid) { rejected = true; }
        h.assertTrue(rejected, "A null action was accepted");
        h.succeed();
    }
}

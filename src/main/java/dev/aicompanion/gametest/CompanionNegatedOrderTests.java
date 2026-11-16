package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** «Не стой», «не защищай меня»: a negated order is no local command, the model gets the phrase. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionNegatedOrderTests {
    @GameTest(template = "empty")
    public static void negatedOrderGoesToTheModel(GameTestHelper h) {
        for (String phrase : new String[]{"не стой столбом", "не жди меня", "не защищай меня", "не иди за мной",
                "не следуй за мной", "не иди ко мне", "не преследуй меня", "не следуйте за мной"})
            h.assertTrue(CompanionLocalCommands.route(phrase, "Олег") == null, phrase + " -> " + CompanionLocalCommands.route(phrase, "Олег"));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void plainOrdersStayLocal(GameTestHelper h) {
        String[][] table = {{"стой", "companion stay"}, {"жди тут", "companion stay"}, {"я не знаю, жди тут", "companion stay"},
                {"защищай меня", "companion guard"}, {"иди за мной", "companion follow"}, {"иди ко мне", "companion summon"},
                // An order next to a negated one is still an order.
                {"стой, не иди туда", "companion stay"}, {"не иди за мной, жди тут", "companion stay"},
                {"не жди, иди за мной", "companion follow"}, {"не иди туда, иди ко мне", "companion summon"}};
        for (String[] row : table)
            h.assertTrue(row[1].equals(CompanionLocalCommands.route(row[0], "Олег")), row[0] + " -> " + CompanionLocalCommands.route(row[0], "Олег"));
        h.succeed();
    }
}

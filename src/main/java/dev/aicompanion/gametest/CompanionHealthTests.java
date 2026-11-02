package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionHealth;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The owner's request of 31.10: his health is shown to the player — a bar in the companion menu and the same block above his head. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionHealthTests {
    /** The bar and the numbers are pure values: full, empty, hurt, broken, clamped and never negative. */
    @GameTest(template = "empty")
    public static void theHealthShownIsBounded(GameTestHelper h) {
        h.assertTrue(CompanionHealth.share(20.0F, 20.0F) == 1.0F, "Full health does not fill the bar");
        h.assertTrue(CompanionHealth.share(10.0F, 20.0F) == 0.5F, "Half health does not fill half the bar");
        h.assertTrue(CompanionHealth.share(0.0F, 20.0F) == 0.0F, "Zero health still fills the bar");
        h.assertTrue(CompanionHealth.share(-4.0F, 20.0F) == 0.0F, "Negative health fills below zero");
        h.assertTrue(CompanionHealth.share(30.0F, 20.0F) == 1.0F, "Health above the maximum overflows the bar");
        h.assertTrue(CompanionHealth.share(10.0F, 0.0F) == 0.0F, "A missing maximum divides by zero");
        h.assertTrue(CompanionHealth.share(10.0F, -5.0F) == 0.0F, "A negative maximum divides by zero");
        h.assertTrue(CompanionHealth.share(Float.NaN, 20.0F) == 0.0F, "A broken value reaches the bar");
        h.assertTrue(CompanionHealth.health(20.0F, 20.0F).equals("20/20"), "Health does not read 20/20: " + CompanionHealth.health(20.0F, 20.0F));
        h.assertTrue(CompanionHealth.health(6.4F, 20.0F).equals("6/20"), "Health does not floor: " + CompanionHealth.health(6.4F, 20.0F));
        h.assertTrue(CompanionHealth.health(19.6F, 20.0F).equals("19/20"), "Health rounds up beside a bar below full: " + CompanionHealth.health(19.6F, 20.0F));
        h.assertTrue(CompanionHealth.health(25.0F, 20.0F).equals("20/20"), "Health above the maximum is not clamped: " + CompanionHealth.health(25.0F, 20.0F));
        h.assertTrue(CompanionHealth.health(-2.0F, 20.0F).equals("0/20"), "Health shows negative points: " + CompanionHealth.health(-2.0F, 20.0F));
        h.assertTrue(CompanionHealth.health(9.5F, 9.5F).equals("9/9"), "A fractional maximum is not floored: " + CompanionHealth.health(9.5F, 9.5F));
        h.assertTrue(CompanionHealth.health(5.0F, 0.0F).equals("0/0"), "A missing maximum still reads: " + CompanionHealth.health(5.0F, 0.0F));
        h.succeed();
    }
}

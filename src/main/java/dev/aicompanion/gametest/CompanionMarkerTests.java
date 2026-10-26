package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Толик 23.10: «было бы удобно чтоб я всегда знал где он, может небольшой маркер на нем если он за стеной», then «оставь только
 * контур». The drawing is the client's; here that the outline is no server state other players would see.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMarkerTests {
    @GameTest(template = "empty")
    public static void theOutlineIsNoServerState(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 2, 1, 2);
        var before = CompanionEntity.clientOutline;
        CompanionEntity.clientOutline = any -> true;
        try {
            h.assertTrue(!companion.isCurrentlyGlowing() && companion.getTeamColor() == 0xFFFFFF, "The server made him glow for everybody");
        } finally { CompanionEntity.clientOutline = before; }
        h.succeed();
    }
}

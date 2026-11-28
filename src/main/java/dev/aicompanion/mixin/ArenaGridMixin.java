package dev.aicompanion.mixin;

import dev.aicompanion.gametest.CompanionArenaSpacingTests;
import net.minecraft.gametest.framework.StructureGridSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The game lays test arenas 5 blocks apart and runs a whole batch at once, so the companion of one arena found the
 * crafting table of the next. The gap is widened past his searches; the server hard-codes this spawner, hence a mixin.
 */
@Mixin(StructureGridSpawner.class)
public abstract class ArenaGridMixin {
    @ModifyConstant(method = "spawnStructure", constant = {@Constant(intValue = 5), @Constant(intValue = 6)})
    private int aicompanion$widerGap(int gap) {
        return CompanionArenaSpacingTests.GAP;
    }
}

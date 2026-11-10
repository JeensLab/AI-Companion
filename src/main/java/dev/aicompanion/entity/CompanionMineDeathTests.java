package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The record of the mine goes through a death: inheritMemory is package-private, so the tests live beside it. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineDeathTests {
    private CompanionMineDeathTests() { }

    /** Dead in the middle of the stairs: the next body goes on down the same mine, and the blocks the dead one held are not given out twice. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aNewBodyGoesOnWithTheMineOfTheOneWhoDied(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        CompanionEntity dead = h.spawn(AICompanionMod.COMPANION.get(), 2, 2, 2);
        dead.setOwnerId(player.getUUID());
        BlockPos entrance = h.absolutePos(new BlockPos(2, 2, 2));
        dead.mineSite().start(entrance, Direction.EAST);
        for (int i = 1; i <= 6; i++) dead.mineSite().reached(entrance.offset(i, -i, 0));
        dead.mineSite().temporary(entrance.offset(3, -3, 0).above());
        dead.mineSite().hold(entrance.offset(6, -6, 0), List.of(new ItemStack(Items.COBBLESTONE, 3)));
        List<BlockPos> stands = List.copyOf(dead.mineSite().stands());
        dead.die(h.getLevel().damageSources().generic());

        CompoundTag memory = CompanionMemorial.get(h.getLevel().getServer()).returned(player.getUUID());
        CompanionEntity back = h.spawn(AICompanionMod.COMPANION.get(), 4, 2, 4);
        back.inheritMemory(memory);
        h.assertTrue(back.mineSite().exists() && entrance.equals(back.mineSite().entrance()) && back.mineSite().stands().equals(stands),
                "The new body lost the mine: " + back.mineSite().stands().size() + " of " + stands.size());
        h.assertTrue(back.mineSite().temporary().equals(dead.mineSite().temporary()), "The temporary blocks were lost: " + back.mineSite().temporary());
        h.assertTrue(!memory.contains(CompanionMineSite.HELD_KEY) && back.mineSite().peekHeld().isEmpty() && !back.mineSite().loadedHeld(),
                "The blocks the dead body held would be dropped a second time");
        h.succeed();
    }

    /** A memory from before the record existed has no mine: it reads, and the new body has none. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void anOldMemoryWithoutTheMineStillReads(GameTestHelper h) {
        CompoundTag old = new CompoundTag();
        old.putString("CompanionName", "Олег");
        CompanionEntity back = h.spawn(AICompanionMod.COMPANION.get(), 4, 2, 4);
        back.inheritMemory(old);
        h.assertTrue(!back.mineSite().exists(), "A memory without a mine left one behind");
        h.succeed();
    }
}

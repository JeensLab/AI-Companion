package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** What a break_block hand-over keeps for the player on the way (CompanionHandOver.reserved). Beside the package-private method it calls. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionHandOverBundleTests {
    private CompanionHandOverBundleTests() { }

    /**
     * «Сломай сундук и отдай мне», the chest with 8 cobblestone and 8 dirt: his own 56 cobblestone and the 8 picked up are one stack ahead of the
     * dirt in the bag. Each kind keeps its own share: the cobblestone does not keep the whole 17 and leave the dirt free to be stood on.
     */
    @GameTest(template = "empty")
    public static void eachKindOfABundleKeepsItsOwnShare(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 1, 1, 1);
        CompanionWork w = c.work;
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        c.setItem(1, new ItemStack(Items.DIRT, 8));
        c.setItem(2, new ItemStack(Items.CHEST));
        w.handOver.bundle = new ArrayList<>(List.of(new ItemStack(Items.CHEST), new ItemStack(Items.COBBLESTONE, 8), new ItemStack(Items.DIRT, 8)));
        w.job = CompanionWork.Job.GIVE;
        w.requestedItem = CompanionHandOver.BUNDLE;
        w.requestedCount = 17;
        h.assertTrue(w.handOver.reserved(c.getItem(0)) == 8, "Cobblestone keeps " + w.handOver.reserved(c.getItem(0)) + ", not its 8");
        h.assertTrue(w.handOver.reserved(c.getItem(1)) == 8, "Dirt keeps " + w.handOver.reserved(c.getItem(1)) + ", not its 8");
        h.assertTrue(w.handOver.reserved(c.getItem(2)) == 1, "The chest keeps " + w.handOver.reserved(c.getItem(2)) + ", not its 1");
        w.job = null;
        h.succeed();
    }
}

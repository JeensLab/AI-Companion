package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionParkHold;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static dev.aicompanion.gametest.CompanionSortingTests.count;
import static dev.aicompanion.gametest.CompanionSortingTests.setup;

/** What the player hears from sorting, and the small rules around it (issue 32, ticket 06c). */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSortingHearingTests {
    private static final List<PlannedAction> SORT = List.of(new PlannedAction("sort_chests", 0, 1));

    /** A refusal or a «things stay in my bag» of the server reaches the player as it is, not as «С этим пока не справился». */
    @GameTest(template = "empty")
    public static void sortRefusalsReachThePlayerAsTheyAre(GameTestHelper helper) {
        for (String said : List.of("Рядом с тобой сундуков не вижу.", "Сумка забита, сначала сложить мои вещи?",
                "Не смог дойти до сундука. Что нёс — у меня в сумке.", "Не получилось разложить всё: не поместилось 3. Часть вещей у меня в сумке.")) {
            String heard = CompanionDialogue.summarize(SORT, "План остановлен: " + said);
            helper.assertTrue(heard.equals(said), "Heard «" + heard + "» instead of «" + said + "»");
        }
        helper.succeed();
    }

    /** «Стоп» in the middle of sorting while he carries things: the plan ends with the server's cancel note, and the player hears it. */
    @GameTest(template = "empty")
    public static void stopMidSortSaysThingsAreInTheBag(GameTestHelper helper) {
        String heard = CompanionDialogue.summarize(SORT, "План остановлен: Работа прервана. Уже полученные вещи сохранены. Часть вещей у меня в сумке.");
        helper.assertTrue(heard.startsWith("Ладно, остановился.") && heard.contains("в сумке"), "Heard: " + heard);
        String bare = CompanionDialogue.summarize(SORT, "План остановлен: Работа прервана. Уже полученные вещи сохранены.");
        helper.assertTrue(bare.equals("Ладно, остановился."), "Bare stop heard: " + bare);
        String other = CompanionDialogue.summarize(List.of(new PlannedAction("gather_stone", 0, 4)), "План остановлен: Заказ отменён.");
        helper.assertTrue(other.equals("Ладно, остановился."), "Another work's stop changed: " + other);
        helper.succeed();
    }

    /** Sorting needs no furnace: a batch cooking in one does not hold it. */
    @GameTest(template = "empty")
    public static void sortingDoesNotWaitForTheFurnace(GameTestHelper helper) {
        var batch = new CompanionParkHold.Parked("minecraft:beef", "minecraft:cooked_beef");
        helper.assertTrue(!CompanionParkHold.held(SORT, List.of(batch), item -> java.util.Set.of()), "Sorting is held for a batch in the furnace");
        helper.succeed();
    }

    /**
     * The home of a stack is fixed from the place where the work began: two chests with stone, the far one nearly full. He is
     * moved next to it right after the start, but the stone still goes to the chest that was nearest at the start.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void storeHomeIsFixedAtTheStartOfTheWork(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        BlockPos nearPos = new BlockPos(10, 1, 10), farPos = new BlockPos(12, 1, 10);
        helper.setBlock(nearPos, net.minecraft.world.level.block.Blocks.CHEST); helper.setBlock(farPos, net.minecraft.world.level.block.Blocks.CHEST);
        Container near = (Container)helper.getBlockEntity(nearPos), far = (Container)helper.getBlockEntity(farPos);
        near.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        for (int slot = 0; slot < 26; slot++) far.setItem(slot, new ItemStack(Items.STONE, 64));
        far.setItem(26, new ItemStack(Items.COBBLESTONE, 60));
        companion.teleportTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 10.5)).x, helper.absolutePos(nearPos).getY(), helper.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 10.5)).z);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 40));
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (companion.hasActiveWorkOrder() && ticks[0]++ == 2) {
                var spot = helper.absoluteVec(new net.minecraft.world.phys.Vec3(13.5, 1, 10.5));
                companion.teleportTo(spot.x, spot.y, spot.z);
            }
        });
        companion.startWork("store_items", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder(), "Still working");
            helper.assertTrue(count(near, Items.COBBLESTONE) == 50, "The stone did not go to the chest that was nearest at the start: " + count(near, Items.COBBLESTONE) + " / far " + count(far, Items.COBBLESTONE));
            helper.assertTrue(count(far, Items.COBBLESTONE) == 60 && count(companion, Items.COBBLESTONE) == 0, "Stone left in the far chest or the bag: " + count(far, Items.COBBLESTONE) + " / bag " + count(companion, Items.COBBLESTONE));
        });
    }
}

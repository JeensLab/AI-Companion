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
                "Не смог дойти до одного из сундуков. Часть вещей у меня в сумке.", "Не получилось разложить всё: не поместилось 3. Часть вещей у меня в сумке.",
                "Не успел закончить работу. Остановился, вещи у меня.")) {
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

    /** Review round 3, finding 1: the note that came with an early step of a plan is said when the plan ends with another step, and once. */
    @GameTest(template = "empty")
    public static void bagNoteIsSaidWhateverStepItCameWith(GameTestHelper helper) {
        String note = CompanionDialogue.BAG_NOTE;
        String wood = CompanionDialogue.summarize(List.of(new PlannedAction("stop", 0, 1), new PlannedAction("gather_wood", 0, 5)), "Ладно, остановился. " + note + "\nСрубил 5 брёвен.");
        String craft = CompanionDialogue.summarize(List.of(new PlannedAction("follow", 0, 1), new PlannedAction("craft_item", 0, 1, "minecraft:wooden_pickaxe")),
                "Следую за тобой. " + note + "\nСкрафтил 1 «Деревянная кирка».");
        String stopped = CompanionDialogue.summarize(SORT, "План остановлен: Работа прервана. Уже полученные вещи сохранены. " + note);
        for (String heard : List.of(wood, craft, stopped))
            helper.assertTrue(heard.contains(note) && heard.indexOf(note) == heard.lastIndexOf(note), "Heard: " + heard);
        helper.succeed();
    }

    /**
     * Review round 3, M2: «стой» says its own «Ладно, жду здесь.» before the note, and an order right after a stop whose note the
     * player already heard (the tasks window, /companion stop) does not say it again.
     */
    @GameTest(template = "empty")
    public static void bagNoteFollowsTheAcknowledgementAndIsNotRepeated(GameTestHelper helper) {
        String note = CompanionDialogue.BAG_NOTE;
        List<PlannedAction> wait = List.of(new PlannedAction("wait", 0, 1)), come = List.of(new PlannedAction("come", 0, 1));
        String first = CompanionDialogue.heard(wait, CompanionDialogue.summarize(wait, "Остановился и жду. " + note), true, false);
        helper.assertTrue(first.equals("Ладно, жду здесь. " + note), "Heard: " + first);
        String again = CompanionDialogue.heard(wait, CompanionDialogue.summarize(wait, "Остановился и жду. " + note), true, true);
        helper.assertTrue(again.equals("Ладно, жду здесь."), "Heard again: " + again);
        String model = CompanionDialogue.heard(come, CompanionDialogue.summarize(come, "Я рядом. " + note), false, true);
        helper.assertTrue(!model.isBlank() && !model.contains(note), "Heard again after a model plan: " + model);
        helper.succeed();
    }

    /** A plan that only has sorting in it is not all sorting: the raw server line of another step (a coordinate, a block id) is not said as it is. */
    @GameTest(template = "empty")
    public static void rawServerLineOfAnotherStepIsNotSaidBecauseSortingIsInThePlan(GameTestHelper helper) {
        List<PlannedAction> plan = List.of(new PlannedAction("place_block", 0, 2, "minecraft:chest"), new PlannedAction("sort_chests", 0, 1));
        String heard = CompanionDialogue.summarize(plan, "План остановлен: На месте установки сейчас 1 из 2 блоков minecraft:chest на 12, 64, -5.");
        helper.assertTrue(!heard.contains("minecraft:") && !heard.matches("(?s).*-?\\d+, -?\\d+, -?\\d+.*"), "Heard the raw line: " + heard);
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

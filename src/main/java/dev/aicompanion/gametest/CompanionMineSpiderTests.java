package dev.aicompanion.gametest;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A monster he may not strike stands at his side: it does not take the turn from the mine. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineSpiderTests {
    private static final int CENTER = 24, GROUND = 21, SETTLED_BY = 200, BAG_SLOT = 6;

    private CompanionMineSpiderTests() { }

    /** A spider on the light is neutral and no target of his: standing beside him at the start of an order it must not hold him with the sword in his hand. */
    @GameTest(template = "marathon_cave", timeoutTicks = SETTLED_BY + 20)
    public static void aSpiderThatLeavesHimAloneDoesNotHoldTheMine(GameTestHelper h) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.setItem(BAG_SLOT, new ItemStack(Items.STONE_SWORD));
        Spider spider = h.spawn(EntityType.SPIDER, CENTER + 1, GROUND + 1, CENTER);
        spider.goalSelector.removeAllGoals(goal -> true);
        spider.targetSelector.removeAllGoals(goal -> true);
        start(h, c, 1);
        h.runAfterDelay(SETTLED_BY, () -> {
            h.assertTrue(spider.isAlive() && !"LOCATE".equals(c.expeditionStage()), "Held by a spider he may not strike, stage " + c.expeditionStage() + ", spider alive " + spider.isAlive() + ", " + c.getLastTaskResult());
            h.succeed();
        });
    }
}

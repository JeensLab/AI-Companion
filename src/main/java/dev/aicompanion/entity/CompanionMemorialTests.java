package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** The list of dead companions is capped: the oldest records are evicted, in memory, after a reload and from an old save. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMemorialTests {
    private static final int EXTRA = 5;
    private static final UUID OWNER = new UUID(7, 7);

    private static UUID dead(int number) { return new UUID(1, number); }

    private static void assertOnlyNewestKept(GameTestHelper h, CompanionMemorial memorial, String where) {
        for (int i = 0; i < EXTRA; i++) h.assertFalse(memorial.isDead(dead(i)), where + ": the oldest record " + i + " survived");
        for (int i = EXTRA; i < CompanionMemorial.MAX_DEAD + EXTRA; i++) h.assertTrue(memorial.isDead(dead(i)), where + ": record " + i + " was lost");
    }

    @GameTest(template = "empty")
    public static void memorialKeepsAtMostTwentyDead(GameTestHelper h) {
        var memorial = new CompanionMemorial();
        for (int i = 0; i < CompanionMemorial.MAX_DEAD + EXTRA; i++) memorial.died(OWNER, dead(i), new CompoundTag(), 100);
        assertOnlyNewestKept(h, memorial, "in memory");
        var saved = memorial.save(new CompoundTag(), h.getLevel().registryAccess());
        h.assertTrue(saved.getList("Dead", 11).size() == CompanionMemorial.MAX_DEAD, "Saved list is not capped: " + saved.getList("Dead", 11).size());
        assertOnlyNewestKept(h, CompanionMemorial.load(saved, h.getLevel().registryAccess()), "after reload");
        h.assertTrue(memorial.awaitsReturn(OWNER), "The owner stopped waiting for his companion");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldMemorialSaveWithTwentyFiveDeadLoadsTwenty(GameTestHelper h) {
        var tag = new CompoundTag();
        var list = new ListTag();
        for (int i = 0; i < CompanionMemorial.MAX_DEAD + EXTRA; i++) list.add(NbtUtils.createUUID(dead(i)));
        tag.put("Dead", list);
        assertOnlyNewestKept(h, CompanionMemorial.load(tag, h.getLevel().registryAccess()), "old save");
        h.succeed();
    }
}

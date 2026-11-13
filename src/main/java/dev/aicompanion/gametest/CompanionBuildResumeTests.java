package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.entity.CompanionBuildPlacement;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionHouseTests.*;

/**
 * «Дострой» goes on with what is unfinished and never starts anything of its own: it says there is nothing, names where the unfinished
 * one is when that is another dimension, takes the name or kind said with it, and with two unfinished houses goes on with the one worked on
 * last. A new order for another building drops the unfinished one only when its place is confirmed.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBuildResumeTests {
    /** A building of the kind «дом»; and the plank house, which its own word names. */
    private static final String HOUSE = "деревенский дом", PLANK = "дом 5×5";
    private static final String NETHER = "minecraft:the_nether";
    private static final BlockPos CORNER = new BlockPos(18, GROUND, 14);

    private static String here(GameTestHelper h) { return h.getLevel().dimension().location().toString(); }

    /** A saved village building (the record the world keeps): unfinished for want of every block. */
    private static CompoundTag building(BlockPos base, String name, String dimension, long touched) {
        CompoundTag build = new CompoundTag();
        build.putLong("Base", base.asLong()); build.putString("Building", name); build.putInt("Rotation", 0); build.putInt("Total", 200);
        build.putString("Dimension", dimension); build.putLongArray("Placed", new long[0]); build.put("Woods", new CompoundTag()); build.putLong("Touched", touched);
        return build;
    }
    /** A saved plank house with nothing of it standing yet. */
    private static CompoundTag plank(BlockPos base, String dimension, long touched) {
        CompoundTag house = new CompoundTag();
        house.putLong("Base", base.asLong()); house.putString("Wood", "oak"); house.putString("Dimension", dimension); house.putLongArray("Placed", new long[0]); house.putLong("Touched", touched);
        return house;
    }
    private static CompoundTag scaffolded(CompoundTag build, BlockPos foot) {
        ListTag list = new ListTag(); list.add(LongTag.valueOf(foot.asLong())); build.put("Scaffold", list);
        return build;
    }
    /** The companion as the save of the world holds him with these records (the same entity reads his own data again). */
    private static CompanionEntity withRecords(CompanionEntity companion, CompoundTag plank, CompoundTag building) {
        CompoundTag data = saved(companion);
        if (plank != null) data.put("SmallHouse", plank);
        if (building != null) data.put("TemplateBuild", building);
        companion.readAdditionalSaveData(data);
        return companion;
    }
    private static void clearSite(GameTestHelper h) {
        for (BlockPos rel : BlockPos.betweenClosed(CORNER.offset(-2, 1, -2), CORNER.offset(10, 12, 10))) h.setBlock(rel, Blocks.AIR);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void resumeWithNothingUnfinishedStartsNothingAndSaysSo(GameTestHelper h) {
        CompanionEntity companion = groundedSetup(h);
        for (String said : new String[]{"resume", "resume кузница", "resume дом", "resume храм"}) {
            var result = companion.startWork("build_house", said, 1);
            h.assertTrue(!result.success() && result.message().contains("Нечего достраивать"), "«" + said + "»: " + result.message());
            h.assertTrue(!companion.hasActiveWorkOrder(), "«" + said + "» started work");
        }
        CompoundTag tag = saved(companion);
        h.assertTrue(!tag.getCompound("SmallHouse").contains("Base") && !tag.getCompound("TemplateBuild").contains("Base"), "A house was started: " + tag.getCompound("SmallHouse"));
        h.succeed();
    }

    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void resumeSaysWhereTheUnfinishedBuildingIsWhenItIsInAnotherDimension(GameTestHelper h) {
        CompanionEntity companion = withRecords(groundedSetup(h), null, building(h.absolutePos(new BlockPos(10, GROUND, 10)), HOUSE, NETHER, 5));
        var result = companion.startWork("build_house", "resume", 1);
        h.assertTrue(!result.success() && result.message().contains("другом измерении") && result.message().contains(HOUSE), result.message());
        h.assertTrue(!companion.hasActiveWorkOrder() && !saved(companion).getCompound("SmallHouse").contains("Base"), "Something was started here");
        h.assertTrue(NETHER.equals(saved(companion).getCompound("TemplateBuild").getString("Dimension")), "The record of the other dimension was lost");
        h.succeed();
    }

    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void resumeWithANameOrKindContinuesOnlyThatBuilding(GameTestHelper h) {
        BlockPos base = h.absolutePos(new BlockPos(24, GROUND, 24));
        CompanionEntity companion = withRecords(groundedSetup(h), null, building(base, HOUSE, here(h), 5));
        for (String other : new String[]{"resume кузница", "resume храм", "resume бревенчатый дом"}) {
            var refused = companion.startWork("build_house", other, 1);
            h.assertTrue(!refused.success() && refused.message().contains("Нечего достраивать") && !companion.hasActiveWorkOrder(), "«" + other + "»: " + refused.message());
        }
        for (String same : new String[]{"resume деревенский дом", "resume дом", "resume"}) {
            var result = companion.startWork("build_house", same, 1);
            h.assertTrue(result.success() && companion.hasActiveWorkOrder() && result.message().contains(HOUSE), "«" + same + "»: " + result.message());
            h.assertTrue(templateBase(companion).equals(base), "«" + same + "» left the unfinished building");
            companion.stopGathering();
        }
        h.succeed();
    }

    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void resumeWithTheNameOfAnotherKindLeavesTheUnfinishedPlankHouseAlone(GameTestHelper h) {
        BlockPos base = h.absolutePos(new BlockPos(24, GROUND, 24));
        CompanionEntity companion = withRecords(groundedSetup(h), plank(base, here(h), 5), null);
        var refused = companion.startWork("build_house", "resume кузница", 1);
        h.assertTrue(!refused.success() && refused.message().contains("Нечего достраивать") && !companion.hasActiveWorkOrder(), refused.message());
        for (String same : new String[]{"resume дом", "resume"}) {
            var result = companion.startWork("build_house", same, 1);
            h.assertTrue(result.success() && companion.hasActiveWorkOrder() && result.message().contains(PLANK), "«" + same + "»: " + result.message());
            h.assertTrue(base(companion).equals(base) && !saved(companion).getCompound("TemplateBuild").contains("Base"), "«" + same + "» started another house");
            companion.stopGathering();
        }
        h.succeed();
    }

    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void withBothUnfinishedAWordWithNoNameContinuesTheOneWorkedOnLastAndSaysWhich(GameTestHelper h) {
        BlockPos planks = h.absolutePos(new BlockPos(8, GROUND, 24)), built = h.absolutePos(new BlockPos(24, GROUND, 24));
        CompanionEntity companion = withRecords(groundedSetup(h), plank(planks, here(h), 100), building(built, HOUSE, here(h), 200));
        var last = companion.startWork("build_house", "resume", 1);
        h.assertTrue(last.success() && last.message().startsWith("Продолжаю «" + HOUSE + "»"), "The building worked on last was not continued and named: " + last.message());
        companion.stopGathering();
        var temple = companion.startWork("build_house", "resume храм", 1);
        h.assertTrue(!temple.success() && !companion.hasActiveWorkOrder(), "«достраивай храм» went on with something: " + temple.message());
        var plain = companion.startWork("build_house", "resume дом", 1);
        h.assertTrue(plain.success() && plain.message().contains(PLANK), "The plank house, which is what «дострой дом» says, was not continued: " + plain.message());
        companion.stopGathering();
        withRecords(companion, plank(planks, here(h), 300), building(built, HOUSE, here(h), 200));
        var newer = companion.startWork("build_house", "resume", 1);
        h.assertTrue(newer.success() && newer.message().startsWith("Продолжаю " + PLANK), "The plank house worked on last was not continued: " + newer.message());
        companion.stopGathering();
        h.succeed();
    }

    /** The queue ends the order that waits for a place before it runs the next one: «дострой» then asks for the place again and starts no other house. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void resumeWhileAnOrderWaitsForItsPlaceAsksForThePlaceAgain(GameTestHelper h) {
        CompanionEntity companion = groundedSetup(h);
        ServerPlayer player = CompanionBuildReviewTests.playerOf(h);
        CompanionBuildPlacement.clientReady(player);
        h.assertTrue(companion.startWork("build_house", VILLAGE_HOUSE, 1).success(), "Order rejected");
        h.runAfterDelay(20, () -> {
            h.assertTrue(VILLAGE_HOUSE.equals(CompanionBuildPlacement.pendingName(player)), "The order does not wait for a place");
            companion.stopGathering();
            var again = companion.startWork("build_house", "resume", 1);
            h.assertTrue(again.success(), again.message());
            h.assertTrue(VILLAGE_HOUSE.equals(CompanionBuildPlacement.pendingName(player)), "«дострой» did not ask for the place again");
            h.assertTrue(!saved(companion).getCompound("SmallHouse").contains("Base"), "A plank house was started for «дострой»");
            companion.stopGathering();
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void theResumeWordsCarryTheNameOrKindToTheServer(GameTestHelper h) {
        String[][] said = {{"дострой", "resume"}, {"достраивай", "resume"}, {"продолжи стройку", "resume"}, {"дострой кузницу", "resume кузница"},
                {"достраивай храм", "resume храм"}, {"дострой таёжный дом", "resume таёжный дом"}, {"дострой пустынный дом", "resume пустынный дом"},
                {"дострой дом", "resume дом"}, {"дострой домик", "resume дом"}, {"достраивай небольшой деревянный дом", "resume дом"}};
        for (String[] pair : said) {
            var parsed = CompanionWorkCommands.parse(pair[0], "Олег");
            h.assertTrue(parsed != null && "build_house".equals(parsed.type()) && pair[1].equals(parsed.item()), "«" + pair[0] + "» is «" + (parsed == null ? null : parsed.item()) + "», not «" + pair[1] + "»");
            // The chat sends a line of words: the name goes through it whole.
            var routed = CompanionLocalCommands.action(CompanionLocalCommands.route("Олег, " + pair[0], "Олег"));
            h.assertTrue("build_house".equals(routed.type()) && pair[1].equals(routed.item()), "«" + pair[0] + "» through the chat is «" + routed.item() + "», not «" + pair[1] + "»");
        }
        for (String item : new String[]{"resume", "resume кузница", "resume дом", "resume храм саванны"})
            h.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\",\"item\":\"" + item + "\"}]")).getFirst().item().equals(item), "«" + item + "» refused by the plan check");
        boolean rejected = false;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\",\"item\":\"resume замок дракона\"}]")); } catch (IllegalStateException expected) { rejected = true; }
        h.assertTrue(rejected, "A name outside the catalogue was accepted after «resume»");
        h.succeed();
    }

    /**
     * An order for another building waits for the player to set it down. Until he does, the unfinished one (and the record of its scaffolding)
     * is the one saved, and his giving the place up leaves it his to finish; once the place is confirmed, it is dropped, its scaffolding
     * still saved until it is taken down, and the player is told.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void anOrderForAnotherBuildingKeepsTheUnfinishedOneUntilItsPlaceIsConfirmed(GameTestHelper h) {
        CompanionEntity companion = groundedSetup(h);
        ServerPlayer player = CompanionBuildReviewTests.playerOf(h);
        CompanionBuildPlacement.clientReady(player);
        BlockPos oldBase = h.absolutePos(new BlockPos(24, GROUND, 24)), foot = h.absolutePos(new BlockPos(26, GROUND, 24));
        withRecords(companion, null, scaffolded(building(oldBase, HOUSE, here(h), 7), foot));
        clearSite(h);
        h.assertTrue(companion.startWork("build_house", "кузница", 1).success(), "Order rejected");
        String asked = CompanionBuildPlacement.pendingName(player);
        h.assertTrue(asked != null && !asked.equals(HOUSE), "No wait for the place: " + asked);
        assertKept(h, companion, oldBase, "Waiting for the place");
        CompanionBuildPlacement.cancel(player);
        assertKept(h, companion, oldBase, "After the player gave the place up");
        var again = companion.startWork("build_house", "resume", 1);
        h.assertTrue(again.success() && again.message().contains(HOUSE), "The old building was not his to finish: " + again.message());
        companion.stopGathering();
        h.assertTrue(companion.startWork("build_house", "кузница", 1).success(), "Second order rejected");
        BlockPos corner = h.absolutePos(CORNER);
        String refused = CompanionBuildPlacement.confirm(player, asked, 0, corner);
        h.assertTrue(refused == null, "Confirmation refused: " + refused);
        CompoundTag now = saved(companion).getCompound("TemplateBuild");
        h.assertTrue(asked.equals(now.getString("Building")) && now.getLong("Base") == corner.asLong(), "The new building is not the saved one: " + now);
        h.assertTrue(now.getList("Scaffold", Tag.TAG_LONG).size() == 1, "The scaffolding of the dropped building is not kept saved until it is taken down: " + now.getList("Scaffold", Tag.TAG_LONG));
        h.assertTrue(companion.getLastTaskResult().contains("«" + HOUSE + "»") && companion.getLastTaskResult().contains("недостроенн"), "The dropped building was not said: " + companion.getLastTaskResult());
        companion.stopGathering();
        h.succeed();
    }
    private static void assertKept(GameTestHelper h, CompanionEntity companion, BlockPos oldBase, String when) {
        CompoundTag kept = saved(companion).getCompound("TemplateBuild");
        h.assertTrue(HOUSE.equals(kept.getString("Building")) && kept.getLong("Base") == oldBase.asLong() && kept.getList("Scaffold", Tag.TAG_LONG).size() == 1,
                when + " the record of the unfinished building is not what was saved: " + kept);
    }

    /** «Недостроенную» is said of a building with blocks to place: not of a finished one with scaffolding left, nor of a finished one in another dimension. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void onlyABuildingWithBlocksToPlaceIsCalledUnfinishedWhenItIsDropped(GameTestHelper h) {
        CompanionEntity companion = groundedSetup(h);
        BlockPos base = h.absolutePos(new BlockPos(24, GROUND, 24));
        for (String dimension : new String[]{here(h), NETHER}) {
            CompoundTag finished = building(base, HOUSE, dimension, 3);
            finished.putInt("Total", 3); finished.putLongArray("Placed", new long[]{0b111});
            withRecords(companion, null, here(h).equals(dimension) ? scaffolded(finished, base.offset(2, 0, 0)) : finished);
            var result = companion.startWork("build_house", "кузница", 1);
            h.assertTrue(result.success() && !result.message().contains("недостроенн"), "A finished building (" + dimension + ") was called unfinished: " + result.message());
            companion.stopGathering();
        }
        h.succeed();
    }
}

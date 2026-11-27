package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.terrain.LandmarkMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLandmarkMemoryTests {
    private static final String OVERWORLD = "minecraft:overworld", NETHER = "minecraft:the_nether";
    private static final BlockPos ORE = new BlockPos(0, 60, 0), VIEW = ORE.south(5);

    private static CompoundTag save(LandmarkMemory memory) {
        CompoundTag tag = new CompoundTag();
        memory.save(tag);
        return tag;
    }

    @GameTest(template = "empty")
    public static void conversationSeesOnlyWitnessedKindsWithoutChangingItsDestination(GameTestHelper h) {
        LandmarkMemory memory = new LandmarkMemory();
        var dimension = Level.OVERWORLD.location();
        var cave = memory.discuss(OVERWORLD, "cave", ORE.east(9), VIEW, 1);
        var firstIron = memory.observe(OVERWORLD, "iron", ORE, VIEW, 2);
        var otherIron = memory.observe(OVERWORLD, "iron", ORE.west(9), VIEW.west(9), 3);
        memory.observe(NETHER, "quartz", ORE, VIEW, 4);
        h.assertTrue(memory.witnessedKinds(dimension).equals("возможный вход в пещеру, железную руду"),
                "Dialogue summary included duplicates or a landmark from another dimension");
        h.assertTrue(memory.recall(OVERWORLD, "last").equals(cave), "Reading the summary replaced the discussed cave");
        memory.forget(firstIron);
        h.assertTrue(memory.witnessedKinds(dimension).contains("железную руду"), "Forgetting one vein removed both");
        memory.forget(otherIron);
        h.assertTrue(memory.witnessedKinds(dimension).equals("возможный вход в пещеру"), "Disproved ore remained in the summary");
        memory.load(new CompoundTag());
        h.assertTrue(memory.witnessedKinds(dimension).isEmpty(), "Old summary survived loading another world");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void landmarkSelectionSurvivesFurtherSightingsAndReload(GameTestHelper h) {
        LandmarkMemory memory = new LandmarkMemory();
        var cave = memory.discuss(OVERWORLD, "cave", ORE.east(9), VIEW, 20);
        var iron = memory.observe(OVERWORLD, "iron", ORE, VIEW, 30);
        h.assertTrue(memory.recall(OVERWORLD, "last").equals(cave), "A passive sighting stole the spoken reference");
        h.assertTrue(memory.recall(OVERWORLD, "ore").equals(iron), "General ore recall selected the cave");
        var same = memory.observe(OVERWORLD, "iron", ORE, VIEW, 31);
        h.assertTrue(same == iron, "Repeated looks churned the memory");
        var refreshed = memory.observe(OVERWORLD, "iron", ORE.west(), VIEW.west(4), 500);
        memory.observe(NETHER, "quartz", ORE, VIEW, 501);
        LandmarkMemory loaded = new LandmarkMemory();
        loaded.load(save(memory));
        var chosen = loaded.recall(OVERWORLD, "last");
        h.assertTrue(chosen.equals(refreshed) && chosen.pos().equals(ORE.west()) && chosen.viewpoint().equals(VIEW.west(4)),
                "Lost the discussed vein or mixed the viewpoint with another sighting");
        loaded.forget(chosen);
        h.assertTrue(loaded.recall(OVERWORLD, "ore") == null && loaded.recall(OVERWORLD, "last") == null,
                "Another vein, dimension or cave leaked through the missing-ore follow-up");
        h.assertTrue(loaded.recall(OVERWORLD, "cave").equals(cave), "Removing the vein removed the cave");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void landmarkMemoryIsBoundedAcrossDimensionsAndLoadsOldWorlds(GameTestHelper h) {
        LandmarkMemory memory = new LandmarkMemory();
        memory.observe(NETHER, "debris", ORE, VIEW, 0);
        for (int i = 0; i < 256; i++) {
            BlockPos pos = ORE.east(4 * i);
            memory.observe(OVERWORLD, "iron", pos, pos.south(4), i + 1);
        }
        CompoundTag saved = save(memory);
        h.assertTrue(saved.getCompound(LandmarkMemory.KEY).getList("Places", Tag.TAG_COMPOUND).size() == 256,
                "Landmark cap changed");
        h.assertTrue(memory.recall(NETHER, "debris") == null, "Oldest landmark survived the global cap");
        memory.recall(OVERWORLD, "cave");
        LandmarkMemory loaded = new LandmarkMemory();
        loaded.load(save(memory));
        h.assertTrue(loaded.recall(OVERWORLD, "last") == null, "Failed cave query silently switched to an ore after reload");
        loaded.load(new CompoundTag());
        h.assertTrue(loaded.recall(OVERWORLD, "last") == null && !save(loaded).contains(LandmarkMemory.KEY),
                "An old world inherited unrelated sightings");
        h.succeed();
    }
}

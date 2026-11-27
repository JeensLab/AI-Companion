package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.terrain.LandmarkObserver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLandmarkSightTests {
    private static final BlockPos BASE = new BlockPos(14, 11, 10);

    private static void put(GameTestHelper h, int x, int y, int z, Block block) {
        h.setBlock(BASE.offset(x, y, z), block);
    }

    private static CompanionEntity walker(GameTestHelper h) {
        for (int x = -6; x <= 6; x++)
            for (int z = -2; z <= 11; z++) put(h, x, 0, z, Blocks.SEA_LANTERN);
        var c = h.spawn(AICompanionMod.COMPANION.get(), BASE.above());
        c.setNoAi(true);
        c.setOnGround(true);
        c.setYHeadRot(0);
        c.setXRot(0);
        return c;
    }

    private static Map<String, BlockPos> sweep(GameTestHelper h, LandmarkObserver sight, CompanionEntity c) {
        Map<String, BlockPos> landmarks = new HashMap<>();
        for (int i = 0; i < 32; i++) {
            int rays = sight.tick(c, landmarks::put);
            h.assertTrue(rays <= 14, "Background sight exceeded its per-tick budget: " + rays);
        }
        return landmarks;
    }

    @GameTest(template = "tall_tree")
    public static void namesEveryVisibleOreAndRecallsDiamondAfterIron(GameTestHelper h) {
        var c = walker(h);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(c.getX() + 2, c.getY(), c.getZ());
        c.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        String dimension = c.level().dimension().location().toString();
        record Specimen(String kind, Block... blocks) {}
        var ores = List.of(
                new Specimen("coal", Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
                new Specimen("copper", Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
                new Specimen("gold", Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE),
                new Specimen("lapis", Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
                new Specimen("redstone", Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
                new Specimen("emerald", Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
                new Specimen("quartz", Blocks.NETHER_QUARTZ_ORE), new Specimen("debris", Blocks.ANCIENT_DEBRIS),
                new Specimen("diamond", Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
                new Specimen("iron", Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE));
        for (var ore : ores) {
            int z = ore.kind().equals("iron") ? 3 : 5;
            var pos = h.absolutePos(BASE.offset(0, 2, z));
            for (var block : ore.blocks()) {
                put(h, 0, 2, z, block);
                var visible = sweep(h, new LandmarkObserver(), c);
                h.assertTrue(pos.equals(visible.get(ore.kind())), "Did not recognize " + block + " as " + ore.kind());
                visible.forEach((kind, at) -> c.landmarks().observe(dimension, kind, at, c.blockPosition(), 1));
            }
        }
        h.assertTrue(c.landmarks().recall(dimension, "last").kind().equals("iron"), "Iron must be the latest sighting");
        var ask = CompanionLocalCommands.action(CompanionLocalCommands.route("Где ты видел алмазы?", "Олег"));
        var answer = CompanionEvents.executeAction(player, ask.type(), ask.item(), ask.count());
        h.assertTrue(answer.message().contains("Я видел алмазную руду")
                && c.landmarks().recall(dimension, "last").pos().equals(h.absolutePos(BASE.offset(0, 2, 5))),
                "Asking about diamonds must select the diamond vein for show: " + answer.message());
        h.succeed();
    }

    @GameTest(template = "tall_tree")
    public static void backgroundSightUsesOnlyLitVisibleFacesAndRests(GameTestHelper h) {
        var c = walker(h);
        var sight = new LandmarkObserver();
        var p = h.absolutePos(BASE.offset(0, 2, 5));
        put(h, 0, 2, 5, Blocks.DEEPSLATE_IRON_ORE);
        put(h, 0, 2, -2, Blocks.DIAMOND_ORE);
        var found = sweep(h, sight, c);
        h.assertTrue(p.equals(found.get("iron")) && !found.containsKey("diamond"), "The viewed iron and the unseen diamond: " + found);
        h.assertTrue(LandmarkObserver.stillVisible(c, "iron", p), "Cannot recognize the seen ore again");
        for (int i = 0; i < 120; i++) h.assertTrue(sight.tick(c, found::put) == 0, "Stationary fan ran again");
        c.setYHeadRot(180);
        h.assertTrue(sweep(h, sight, c).containsKey("diamond"), "Turning the head did not expose the diamond");
        c.setYHeadRot(0);
        for (int x = -2; x <= 2; x++) for (int y = 1; y <= 5; y++) put(h, x, y, 3, Blocks.STONE);
        h.assertTrue(!sweep(h, sight, c).containsKey("iron") && !LandmarkObserver.stillVisible(c, "iron", p)
                && !LandmarkObserver.visiblyGone(c, "iron", p), "Occlusion gave information about hidden ore");
        for (int y = 1; y <= 5; y++) put(h, 0, y, 3, Blocks.AIR);
        put(h, 0, 2, 5, Blocks.AIR);
        h.assertTrue(LandmarkObserver.visiblyGone(c, "iron", p), "A mined-out, visible cell should invalidate the ore");
        h.succeed();
    }

    @GameTest(template = "tall_tree")
    public static void aCaveNeedsAVisibleRockLipFloorAndSide(GameTestHelper h) {
        var c = walker(h);
        for (int x = -2; x <= 2; x++) for (int z = 3; z <= 8; z++) put(h, x, 4, z, Blocks.STONE);
        h.assertTrue(!sweep(h, new LandmarkObserver(), c).containsKey("cave"), "An unsupported roof became a cave");
        for (int z = 3; z <= 8; z++) for (int y = 1; y <= 3; y++) {
            put(h, -2, y, z, Blocks.STONE);
            put(h, 2, y, z, Blocks.STONE);
        }
        var cave = sweep(h, new LandmarkObserver(), c).get("cave");
        h.assertTrue(cave != null, "Did not notice the opening beneath the rock lip");
        h.assertTrue(LandmarkObserver.stillVisible(c, "cave", cave), "The remembered opening could not be seen again");
        h.getLevel().setBlockAndUpdate(cave.above(), Blocks.COBBLESTONE.defaultBlockState());
        h.assertTrue(!LandmarkObserver.visiblyGone(c, "cave", cave), "One step on the floor made him forget the whole opening");
        h.getLevel().setBlockAndUpdate(cave.above(2), Blocks.COBBLESTONE.defaultBlockState());
        h.assertTrue(!LandmarkObserver.stillVisible(c, "cave", cave), "A blocked mouth remained confirmed");
        h.assertTrue(LandmarkObserver.visiblyGone(c, "cave", cave),
                "Could not see the block that filled the opening at " + h.relativePos(cave));
        h.succeed();
    }

    @GameTest(template = "tall_tree", timeoutTicks = 160)
    public static void backgroundSightCannotIdentifyOreInADarkGallery(GameTestHelper h) {
        var c = walker(h);
        for (int x = -6; x <= 6; x++) for (int z = -2; z <= 11; z++) {
            put(h, x, 0, z, Blocks.STONE);
            put(h, x, 5, z, Blocks.STONE);
            if (Math.abs(x) == 6 || z == -2 || z == 11) for (int y = 1; y < 5; y++) put(h, x, y, z, Blocks.STONE);
        }
        put(h, 0, 2, 5, Blocks.IRON_ORE);
        BlockPos ore = h.absolutePos(BASE.offset(0, 2, 5));
        h.succeedWhen(() -> {
            h.assertTrue(h.getLevel().getMaxLocalRawBrightness(ore.north()) < 4, "Waiting for gallery darkness");
            h.assertTrue(!sweep(h, new LandmarkObserver(), c).containsKey("iron")
                    && !LandmarkObserver.stillVisible(c, "iron", ore), "Unlit ore became a confirmed observation");
        });
    }
}

package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.terrain.TerrainSurvey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionTerrainTests {
    private static final int X = 16, Z = 12, FLOOR = 6;

    private static void put(GameTestHelper h, int x, int y, int z, Block block) {
        h.setBlock(new BlockPos(X + x, FLOOR + y, Z + z), block);
    }

    private static CompanionEntity observer(GameTestHelper h) {
        var c = h.spawn(AICompanionMod.COMPANION.get(), X, FLOOR + 1, Z);
        c.setYHeadRot(0);
        return c;
    }

    private static void floor(GameTestHelper h) {
        for (int x = -7; x <= 7; x++) for (int z = -6; z <= 12; z++) put(h, x, 0, z, Blocks.SEA_LANTERN);
    }

    @GameTest(template = "tall_tree")
    public static void surveyCommandsAreLocalAndLeaveWanderingAvailable(GameTestHelper h) {
        h.assertTrue("companion work survey_terrain 1 around".equals(CompanionLocalCommands.route("Олег, осмотрись!", "Олег")), "Look request");
        h.assertTrue("companion work survey_terrain 1 descent".equals(CompanionLocalCommands.route("Где здесь спуститься?", "Олег")), "Descent question");
        h.assertTrue("companion roam".equals(CompanionLocalCommands.route("прогуляйся", "Олег")), "Walk request");
        h.assertTrue(CompanionLocalCommands.route("не осмотрись, а добудь дерево", "Олег") == null
                || !CompanionLocalCommands.route("не осмотрись, а добудь дерево", "Олег").contains("survey_terrain"), "Compound request swallowed");
        h.succeed();
    }

    @GameTest(template = "tall_tree")
    public static void landmarkQuestionsUseMemoryAndLeaveOtherOrdersAlone(GameTestHelper h) {
        var recall = CompanionLocalCommands.action(CompanionLocalCommands.route("Олег, мы пещеру по дороге не видели?", "Олег"));
        h.assertTrue(recall.type().equals("recall_landmark") && recall.item().equals("cave"), "Cave memory question");
        var guide = CompanionLocalCommands.action(CompanionLocalCommands.route("Покажи мне, где ты видел железо", "Олег"));
        h.assertTrue(guide.type().equals("guide_landmark") && guide.item().equals("iron"), "Named guide");
        h.assertTrue("companion work guide_landmark 1 last".equals(CompanionLocalCommands.route("Олег, покажи!", "Олег")), "Follow-up");
        String other = CompanionLocalCommands.route("Покажи пещеру и добудь уголь", "Олег");
        h.assertTrue(other == null || !other.contains("landmark"), "Compound order swallowed");
        for (var action : java.util.List.of("recall_landmark", "guide_landmark")) {
            var step = new com.google.gson.JsonObject();
            step.addProperty("action", action);
            step.addProperty("item", "ore");
            var plan = new com.google.gson.JsonArray();
            plan.add(step);
            h.assertTrue(CompanionPlanCodec.parsePlan(plan).size() == 1, action);
            step.addProperty("item", "minecraft:chest");
            boolean rejected = false;
            try { CompanionPlanCodec.parsePlan(plan); }
            catch (IllegalStateException expected) { rejected = true; }
            h.assertTrue(rejected, "Unexpected landmark target accepted");
        }
        h.succeed();
    }

    @GameTest(template = "tall_tree", timeoutTicks = 240)
    public static void walkingCompanionRemembersVisibleOreWithoutASurvey(GameTestHelper h) {
        floor(h);
        for (int x = -2; x <= 2; x++) for (int y = 1; y <= 3; y++)
            put(h, x, y, 12, Blocks.DEEPSLATE_IRON_ORE);
        var c = observer(h);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(c.getX(), c.getY(), c.getZ() + 10);
        c.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        var start = c.position();
        c.setFollowing(true);
        h.succeedWhen(() -> {
            h.assertTrue(c.position().distanceToSqr(start) > 1, "Waiting for normal follow movement");
            var request = CompanionLocalCommands.action(CompanionLocalCommands.route("Где ты видел железо?", "Олег"));
            var response = CompanionEvents.executeAction(player, request.type(), request.item(), request.count());
            h.assertTrue(response.message().startsWith("Я видел железную руду"), response.message());
            h.assertTrue(c.isFollowing() && !c.hasActiveWorkOrder(), "Asking about ore displaced following");
            var saved = new net.minecraft.nbt.CompoundTag();
            c.addAdditionalSaveData(saved);
            var restored = AICompanionMod.COMPANION.get().create(h.getLevel());
            restored.readAdditionalSaveData(saved);
            var entry = restored.landmarks().recall(h.getLevel().dimension().location().toString(), "last");
            h.assertTrue(entry != null && entry.kind().equals("iron") && h.getLevel().getBlockState(entry.pos()).is(Blocks.DEEPSLATE_IRON_ORE),
                    "Remembered ore or conversation target was lost in entity NBT");
        });
    }

    @GameTest(template = "tall_tree")
    public static void showRefersToTheCaveJustRecalledInChat(GameTestHelper h) {
        floor(h);
        for (int z = 3; z <= 8; z++) {
            for (int x = -2; x <= 2; x++) put(h, x, 4, z, Blocks.STONE);
            for (int y = 1; y <= 3; y++) {
                put(h, -2, y, z, Blocks.STONE);
                put(h, 2, y, z, Blocks.STONE);
            }
        }
        var c = observer(h);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(c.getX() + 1, c.getY(), c.getZ() + 2);
        c.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        long[] workId = {0};
        h.startSequence().thenWaitUntil(() -> {
            var ask = CompanionLocalCommands.action(CompanionLocalCommands.route("Мы пещеру по дороге не видели?", "Олег"));
            var reply = CompanionEvents.executeAction(player, ask.type(), ask.item(), ask.count());
            h.assertTrue(reply.message().startsWith("Я видел возможный вход"), reply.message());
        }).thenExecute(() -> {
            var show = CompanionLocalCommands.action(CompanionLocalCommands.route("Покажи!", "Олег"));
            var result = CompanionEvents.executeAction(player, show.type(), show.item(), show.count());
            h.assertTrue(result.status() == CompanionActionResult.Status.ACCEPTED, result.message());
            workId[0] = c.activeWorkId();
        }).thenWaitUntil(() -> {
            var arrival = c.workResult(workId[0]);
            h.assertTrue(arrival != null && arrival.status() == CompanionActionResult.Status.SUCCESS
                    && arrival.message().contains("вход в пещеру"), arrival == null ? "Still guiding" : arrival.message());
        }).thenSucceed();
    }

    @GameTest(template = "tall_tree")
    public static void mixedTerrainPlansMustValidateInFull(GameTestHelper h) {
        var survey = new com.google.gson.JsonObject();
        survey.addProperty("action", "survey_terrain");
        var steps = new com.google.gson.JsonArray();
        steps.add(com.google.gson.JsonParser.parseString("{\"action\":\"wait\"}"));
        steps.add(survey);
        for (String topic : TerrainSurvey.TOPICS) {
            survey.addProperty("item", topic);
            h.assertTrue(CompanionPlanCodec.parsePlan(steps).size() == 2, topic);
        }
        for (String field : java.util.List.of("count", "location", "item")) {
            var invalid = steps.deepCopy();
            var step = invalid.get(1).getAsJsonObject();
            if (field.equals("count")) step.addProperty(field, 2); else step.addProperty(field, "somewhere");
            boolean rejected = false;
            try { CompanionPlanCodec.parsePlan(invalid); }
            catch (IllegalStateException expected) { rejected = true; }
            h.assertTrue(rejected, "Accepted an invalid survey " + field);
        }
        h.succeed();
    }

    @GameTest(template = "tall_tree")
    public static void aSecondLookDoesNotReplaceWorkAndStopCancelsTheSurvey(GameTestHelper h) {
        floor(h);
        var c = observer(h);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(c.getX() - 2, c.getY(), c.getZ());
        c.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        var started = CompanionEvents.executeAction(player, "survey_terrain", "around", 1);
        long id = c.activeWorkId();
        h.assertTrue(started.status() == CompanionActionResult.Status.ACCEPTED && id > 0, started.message());
        var glance = CompanionEvents.executeAction(player, "survey_terrain", "biome", 1);
        h.assertTrue(glance.status() == CompanionActionResult.Status.SUCCESS && c.activeWorkId() == id, glance.message());
        c.stopGatheringForPlayer();
        h.runAfterDelay(25, () -> {
            h.assertTrue(c.activeWorkId() == 0 && c.workResult(id) != null &&
                    c.workResult(id).status() == CompanionActionResult.Status.CANCELLED, "Survey kept running after stop");
            h.succeed();
        });
    }

    @GameTest(template = "tall_tree")
    public static void twoVisibleGroupsSuggestABiomeTransition(GameTestHelper h) {
        floor(h);
        var c = observer(h);
        put(h, -3, 0, 4, Blocks.PODZOL);
        put(h, -3, 1, 5, Blocks.SPRUCE_LOG);
        put(h, 3, 0, 4, Blocks.SAND);
        put(h, 3, 0, 5, Blocks.SAND);
        put(h, 3, 1, 5, Blocks.CACTUS);
        String seen = TerrainSurvey.glance(c, "biome");
        h.assertTrue(seen.contains("переход между биомами") && seen.contains("подзол") && seen.contains("песок"), seen);
        h.succeed();
    }

    @GameTest(template = "tall_tree")
    public static void aLongHighSpineOrLowChannelNeedsBothFlanks(GameTestHelper h) {
        floor(h);
        var c = observer(h);
        c.setPos(c.getX(), c.getY() + 4, c.getZ());
        for (int z = 3; z <= 7; z++) put(h, 0, 2, z, Blocks.STONE);
        String ridge = TerrainSurvey.glance(c, "around");
        h.assertTrue(ridge.contains("гребень"), ridge);
        for (int z = 3; z <= 7; z++) {
            put(h, 0, 2, z, Blocks.AIR);
            put(h, -2, 2, z, Blocks.STONE);
            put(h, 2, 2, z, Blocks.STONE);
        }
        String channel = TerrainSurvey.glance(c, "around");
        h.assertTrue(channel.contains("овраг"), channel);
        h.succeed();
    }

    @GameTest(template = "tall_tree")
    public static void aMonsterBehindAStoneWallIsNotReported(GameTestHelper h) {
        floor(h);
        var c = observer(h);
        var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, X, FLOOR + 1, Z + 5);
        zombie.setNoAi(true);
        for (int x = -2; x <= 2; x++) for (int y = 1; y <= 4; y++) put(h, x, y, 2, Blocks.STONE);
        h.assertTrue(!TerrainSurvey.glance(c, "around").contains("монстра"), "Saw a monster behind stone");
        for (int y = 1; y <= 4; y++) put(h, 0, y, 2, Blocks.AIR);
        String seen = TerrainSurvey.glance(c, "around");
        h.assertTrue(seen.contains("монстра"), seen);
        h.succeed();
    }

    @GameTest(template = "tall_tree")
    public static void eyesSeeBiomeCluesOnlyAfterTheWallIsOpened(GameTestHelper h) {
        floor(h);
        var c = observer(h);
        for (int x = -4; x <= 4; x++) for (int y = 1; y <= 4; y++) put(h, x, y, 2, Blocks.STONE);
        for (int x = -2; x <= 2; x++) put(h, x, 0, 4, Blocks.PODZOL);
        put(h, 0, 1, 5, Blocks.SPRUCE_LOG);
        h.assertTrue(!TerrainSurvey.glance(c, "biome").contains("тайг"), "Saw taiga through stone");
        for (int x = -2; x <= 2; x++) for (int y = 1; y <= 4; y++) put(h, x, y, 2, Blocks.AIR);
        String seen = TerrainSurvey.glance(c, "biome");
        h.assertTrue(seen.contains("Похоже на тайгу"), seen);
        h.succeed();
    }

    @GameTest(template = "tall_tree")
    public static void onlyAContinuousVisibleShallowCrossingSuggestsAFord(GameTestHelper h) {
        floor(h);
        var c = observer(h);
        for (int x = -7; x <= 7; x++) {
            for (int z = 2; z <= 3; z++) {
                put(h, x, -1, z, Blocks.SEA_LANTERN);
                put(h, x, 0, z, Blocks.WATER);
            }
        }
        String distant = TerrainSurvey.glance(c, "ford");
        h.assertTrue(!distant.contains("Похоже на брод"), "Near lip hides the bottom from here");
        c.setPos(c.getX(), c.getY(), c.getZ() + 1);
        String seen = TerrainSurvey.glance(c, "ford");
        h.assertTrue(seen.contains("Вижу берег") && seen.contains("Похоже на брод"), seen);
        for (int x = -7; x <= 7; x++) put(h, x, -1, 3, Blocks.WATER);
        seen = TerrainSurvey.glance(c, "ford");
        h.assertTrue(seen.contains("Вижу берег") && !seen.contains("Похоже на брод"), seen);
        h.succeed();
    }

    @GameTest(template = "tall_tree")
    public static void aSurveyNamesOnlyAnOpeningItCanShow(GameTestHelper h) {
        floor(h);
        var c = observer(h);
        c.setOnGround(true);
        String dimension = c.level().dimension().location().toString();
        var last = c.landmarks().discuss(dimension, "iron", c.blockPosition().west(8), c.blockPosition(), 0);
        for (int x = -2; x <= 2; x++) for (int z = 3; z <= 6; z++) put(h, x, 4, z, Blocks.STONE);
        String seen = TerrainSurvey.glance(c, "cave");
        h.assertTrue(!seen.contains("Похоже на пещеру") && c.landmarks().recall(dimension, "last").equals(last),
                "A roof without walls was reported as a cave: " + seen);
        for (int z = 3; z <= 6; z++) for (int y = 1; y <= 3; y++) {
            put(h, -2, y, z, Blocks.STONE);
            put(h, 2, y, z, Blocks.STONE);
        }
        seen = TerrainSurvey.glance(c, "cave");
        h.assertTrue(seen.contains("под каменным сводом") && c.landmarks().recall(dimension, "last").kind().equals("cave"),
                "The visible cave and the destination of show must agree: " + seen);
        for (int x = -3; x <= 3; x++) for (int y = 1; y <= 6; y++) put(h, x, y, 2, Blocks.STONE);
        h.assertTrue(!TerrainSurvey.glance(c, "cave").contains("под каменным сводом"), "Saw the opening through a wall");
        h.succeed();
    }

    @GameTest(template = "tall_tree")
    public static void aCliffAndAShelfCanBeSeenFromTheSide(GameTestHelper h) {
        floor(h);
        // Two storeys above the opposite floor, with a shelf halfway up the face.
        for (int x = -4; x <= 4; x++) for (int z = 3; z <= 8; z++) {
            int height = z < 5 ? 2 : 6;
            for (int y = 1; y <= height; y++) put(h, x, y, z, Blocks.STONE);
        }
        var c = observer(h);
        put(h, -5, 3, 3, Blocks.SEA_LANTERN);
        c.setPos(c.getX() - 5, c.getY() + 3, c.getZ() + 3);
        c.setYHeadRot(-65);
        String seen = TerrainSurvey.glance(c, "descent");
        h.assertTrue(seen.contains("обрыв") && seen.contains("уступ"), seen);
        h.succeed();
    }

    @GameTest(template = "tall_tree", timeoutTicks = 180)
    public static void surveyWalksToTheVisibleBankAndLooksAgain(GameTestHelper h) {
        floor(h);
        for (int x = -7; x <= 7; x++) for (int z = 5; z <= 6; z++) {
            put(h, x, -1, z, Blocks.SEA_LANTERN);
            put(h, x, 0, z, Blocks.WATER);
        }
        var c = observer(h);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(c.getX() - 2, c.getY(), c.getZ());
        c.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        double z0 = c.getZ();
        var accepted = CompanionEvents.executeAction(player, "survey_terrain", "ford", 1);
        long id = c.activeWorkId();
        h.assertTrue(accepted.success() && id > 0, accepted.message());
        h.succeedWhen(() -> {
            var result = c.workResult(id);
            h.assertTrue(result != null, "Still walking to the bank");
            h.assertTrue(c.getZ() > z0 + 2 && result.message().contains("Есть проход вброд"), result.message() + " moved " + (c.getZ() - z0));
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 180)
    public static void descentIsReportedOnlyAfterSurveyWorkFinishes(GameTestHelper h) {
        for (int x = -5; x <= 5; x++) for (int z = -4; z <= 10; z++) {
            int y = -Math.max(0, Math.min(4, z));
            put(h, x, y, z, Blocks.SEA_LANTERN);
        }
        var c = observer(h);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(c.getX() - 2, c.getY(), c.getZ());
        c.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        var start = CompanionEvents.executeAction(player, "survey_terrain", "descent", 1);
        long id = c.activeWorkId();
        h.assertTrue(start.success() && id > 0 && c.workResult(id) == null, start.message());
        h.succeedWhen(() -> {
            var result = c.workResult(id);
            h.assertTrue(result != null, "Still inspecting");
            h.assertTrue(result.success() && result.message().contains("можно спуститься") && result.message().contains("можно подняться"), result.message());
        });
    }
}

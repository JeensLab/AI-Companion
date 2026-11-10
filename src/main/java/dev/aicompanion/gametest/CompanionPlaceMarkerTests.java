package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlaceSpeech;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionMarkerBoard;
import dev.aicompanion.entity.CompanionMarkerNames;
import dev.aicompanion.entity.CompanionMarkers;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * «Запомни это место, дача» (3GFBJ): named markers of the player — saving by phrase, recognition in free speech by
 * Russian word forms, one honest question for an unknown or ambiguous name, a real walk by the normal path, another
 * dimension said as it is, renaming, deleting, coordinates, the cap and its eviction, persistence, the settings switch,
 * and the two plan actions with their codec and runner wiring.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPlaceMarkerTests {

    // ---- the phrase: what a marker is called ----

    /** «запомни это место, это дом», «запомни это дом2», «запомни это колодец»: the words after «это» are the name. */
    @GameTest(template = "empty")
    public static void rememberingPhrasesSaveTheNameTheySay(GameTestHelper h) {
        List<String> markers = List.of();
        h.assertTrue("companion place remember дом".equals(CompanionLocalCommands.route("запомни это место, это дом", "Олег", markers, true)),
                "«место, это дом»: " + CompanionLocalCommands.route("запомни это место, это дом", "Олег", markers, true));
        h.assertTrue("companion place remember дом2".equals(CompanionLocalCommands.route("запомни это дом2", "Олег", markers, true)),
                "Digits in a name: " + CompanionLocalCommands.route("запомни это дом2", "Олег", markers, true));
        h.assertTrue("companion place remember колодец".equals(CompanionLocalCommands.route("запомни это колодец", "Олег", markers, true)),
                "Well: " + CompanionLocalCommands.route("запомни это колодец", "Олег", markers, true));
        h.assertTrue("companion place remember железный рудник".equals(CompanionLocalCommands.route("Олег, запомни это железный рудник!", "Олег", markers, true)),
                "Name with a space: " + CompanionLocalCommands.route("Олег, запомни это железный рудник!", "Олег", markers, true));
        h.assertTrue("companion place remember дача".equals(CompanionLocalCommands.route("запомни это \"дача\"", "Олег", markers, true)),
                "Quotes: " + CompanionLocalCommands.route("запомни это \"дача\"", "Олег", markers, true));
        // The name may be exactly 24 characters; 25 are too long.
        String longest = "а".repeat(CompanionMarkerNames.MAX);
        h.assertTrue(("companion place remember " + longest).equals(CompanionLocalCommands.route("запомни это " + longest, "Олег", markers, true)),
                "24 characters: " + CompanionLocalCommands.route("запомни это " + longest, "Олег", markers, true));
        h.assertTrue(CompanionLocalCommands.route("запомни это " + longest + "а", "Олег", markers, true) == null,
                "25 characters were taken");
        // An empty name is not a marker: one honest question, no command.
        var empty = CompanionPlaceSpeech.parse("запомни это место", "Олег", markers, true);
        h.assertTrue(empty.kind() == CompanionPlaceSpeech.Kind.EMPTY_NAME, "An empty name: " + empty.kind());
        h.assertTrue(empty.question() != null && empty.question().contains("Как назвать"), "No honest question: " + empty.question());
        h.assertTrue(CompanionLocalCommands.route("запомни это место", "Олег", markers, true) == null, "An empty name became a command");
        // The home phrases keep their meaning, and the work grammar is checked before markers.
        h.assertTrue("companion work set_home 1 resources".equals(CompanionLocalCommands.route("запомни это место как дом", "Олег", markers, true)),
                "The home phrase was taken as a marker: " + CompanionLocalCommands.route("запомни это место как дом", "Олег", markers, true));
        h.succeed();
    }

    /** A marker is saved where the companion stands, with its dimension and creation time. */
    @GameTest(template = "empty")
    public static void theMarkerIsSavedAtTheCompanionsPoint(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        var result = CompanionEvents.executeAction(player(h, c), "remember_place", "колодец", 1);
        h.assertTrue(result.success(), "The marker was refused: " + result.message());
        h.assertTrue(result.message().contains("Запомнил место «колодец»"), "No honest answer: " + result.message());
        var markers = CompanionMarkers.get(h.getLevel().getServer()).all(c.getOwnerId());
        h.assertTrue(markers.size() == 1, "Not saved: " + markers);
        var marker = markers.getFirst();
        h.assertTrue(marker.pos().equals(c.blockPosition()), "Saved elsewhere: " + marker.pos() + " he at " + c.blockPosition());
        h.assertTrue(marker.dimension().equals(h.getLevel().dimension()), "Another dimension: " + marker.dimension());
        h.assertTrue(marker.created() > 0, "No creation time: " + marker.created());
        h.succeed();
    }

    // ---- free speech: the five verbs and the Russian word forms ----

    /** «иди к колодцу», «проведи меня до железного рудника», «отведи меня к даче», «охраняй дачу», «отнеси ресы в дом». */
    @GameTest(template = "empty")
    public static void theFiveVerbsResolveTheMarkerByWordForms(GameTestHelper h) {
        List<String> markers = List.of("колодец", "железный рудник", "дача", "дом");
        h.assertTrue("companion place go колодец".equals(CompanionLocalCommands.route("иди к колодцу", "Олег", markers, true)),
                "«иди к колодцу»: " + CompanionLocalCommands.route("иди к колодцу", "Олег", markers, true));
        h.assertTrue("companion place go железный рудник".equals(CompanionLocalCommands.route("проведи меня до железного рудника", "Олег", markers, true)),
                "«проведи меня до железного рудника»: " + CompanionLocalCommands.route("проведи меня до железного рудника", "Олег", markers, true));
        h.assertTrue("companion place go дача".equals(CompanionLocalCommands.route("отведи меня к даче", "Олег", markers, true)),
                "«отведи меня к даче»: " + CompanionLocalCommands.route("отведи меня к даче", "Олег", markers, true));
        h.assertTrue("companion place guard дача".equals(CompanionLocalCommands.route("охраняй дачу", "Олег", markers, true)),
                "«охраняй дачу»: " + CompanionLocalCommands.route("охраняй дачу", "Олег", markers, true));
        h.assertTrue("companion place carry дом".equals(CompanionLocalCommands.route("отнеси ресы в дом", "Олег", markers, true)),
                "«отнеси ресы в дом»: " + CompanionLocalCommands.route("отнеси ресы в дом", "Олег", markers, true));
        // Case does not matter, and the name keeps its own spelling in the answer.
        h.assertTrue("companion place go Колодец".equals(CompanionLocalCommands.route("иди к КОЛОДЦУ", "Олег", List.of("Колодец"), true)),
                "Case: " + CompanionLocalCommands.route("иди к КОЛОДЦУ", "Олег", List.of("Колодец"), true));
        // The guard and carry orders carry their second step: guard the place, carry the things there.
        var guard = CompanionLocalCommands.actions("companion place guard дача");
        h.assertTrue(guard.size() == 2 && "go_to_place".equals(guard.get(0).type()) && "protect".equals(guard.get(1).type()), "Guard plan: " + guard);
        var carry = CompanionLocalCommands.actions("companion place carry дом");
        h.assertTrue(carry.size() == 2 && "go_to_place".equals(carry.get(0).type()) && "store_items".equals(carry.get(1).type())
                && "resources".equals(carry.get(1).item()), "Carry plan: " + carry);
        h.succeed();
    }

    /** «Охраняй меня» is still the guard order, and «до дома» without a marker stays the model's, not a question. */
    @GameTest(template = "empty")
    public static void peopleAndTheHomeWordAreNotMarkers(GameTestHelper h) {
        h.assertTrue("companion guard".equals(CompanionLocalCommands.route("охраняй меня", "Олег", List.of("дача"), true)),
                "«охраняй меня»: " + CompanionLocalCommands.route("охраняй меня", "Олег", List.of("дача"), true));
        var home = CompanionPlaceSpeech.parse("проведи меня до дома", "Олег", List.of("дача"), true);
        h.assertTrue(home.kind() == CompanionPlaceSpeech.Kind.NONE && home.question() == null,
                "«до дома» became a question: " + home.kind() + " " + home.question());
        h.succeed();
    }

    // ---- one honest question, never a guess ----

    /** An unknown name: exactly one question with the known names, and no trip. */
    @GameTest(template = "empty")
    public static void anUnknownNameAsksOnceAndStartsNoTrip(GameTestHelper h) {
        var result = CompanionPlaceSpeech.parse("иди к озеру", "Олег", List.of("дача", "колодец"), true);
        h.assertTrue(result.kind() == CompanionPlaceSpeech.Kind.UNKNOWN, "Unknown name: " + result.kind());
        h.assertTrue(result.question() != null && result.question().contains("«дача»") && result.question().contains("«колодец»"),
                "The known names are not listed: " + result.question());
        h.assertTrue(result.question().chars().filter(ch -> ch == '?').count() == 1, "Not one question: " + result.question());
        h.assertTrue(CompanionLocalCommands.route("иди к озеру", "Олег", List.of("дача", "колодец"), true) == null,
                "An unknown name started a trip");
        // No markers at all: he says so and tells how to make one.
        var none = CompanionPlaceSpeech.parse("иди к озеру", "Олег", List.of(), true);
        h.assertTrue(none.kind() == CompanionPlaceSpeech.Kind.UNKNOWN && none.question().contains("Меток пока нет"),
                "No markers: " + none.question());
        h.succeed();
    }

    /** Two markers that fit the same name: one question, no trip, no guess. */
    @GameTest(template = "empty")
    public static void twoSimilarMarkersAskOnceAndStartNoTrip(GameTestHelper h) {
        var result = CompanionPlaceSpeech.parse("иди к колодцу", "Олег", List.of("колодец", "колодцы"), true);
        h.assertTrue(result.kind() == CompanionPlaceSpeech.Kind.AMBIGUOUS, "Similar markers: " + result.kind());
        h.assertTrue(result.question().contains("«колодец»") && result.question().contains("«колодцы»"), "Both not named: " + result.question());
        h.assertTrue(result.question().chars().filter(ch -> ch == '?').count() == 1, "Not one question: " + result.question());
        h.assertTrue(CompanionLocalCommands.route("иди к колодцу", "Олег", List.of("колодец", "колодцы"), true) == null,
                "An ambiguous name started a trip");
        h.succeed();
    }

    /** At run time too: an unknown or ambiguous name is answered honestly, and no work order starts. */
    @GameTest(template = "empty")
    public static void theServerAnswersAnUnknownNameHonestly(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition().offset(4, 0, 0), 1);
        var unknown = CompanionEvents.executeAction(owner, "go_to_place", "озеро", 1);
        h.assertTrue(!unknown.success() && unknown.message().contains("Не знаю метки «озеро»") && unknown.message().contains("«дача»"),
                "No honest answer: " + unknown.message());
        h.assertTrue(c.workPhase().isEmpty(), "A trip started for an unknown name: " + c.workPhase());
        store.add(c.getOwnerId(), "колодец", h.getLevel().dimension(), c.blockPosition().offset(8, 0, 0), 2);
        store.add(c.getOwnerId(), "колодцы", h.getLevel().dimension(), c.blockPosition().offset(6, 0, 0), 3);
        var ambiguous = CompanionEvents.executeAction(owner, "go_to_place", "колодцу", 1);
        h.assertTrue(!ambiguous.success() && ambiguous.message().contains("«колодец»") && ambiguous.message().contains("«колодцы»"),
                "Ambiguity not said: " + ambiguous.message());
        h.assertTrue(ambiguous.message().chars().filter(ch -> ch == '?').count() == 1, "Not one question: " + ambiguous.message());
        h.assertTrue(c.workPhase().isEmpty(), "A trip started for an ambiguous name: " + c.workPhase());
        h.succeed();
    }

    // ---- the walk ----

    /** A marker ten blocks away: he walks there by the normal path and says where he arrived. */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void heWalksToAMarkerInTheSameDimension(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        BlockPos marker = h.absolutePos(new BlockPos(18, 1, 8));
        CompanionMarkers.get(h.getLevel().getServer()).add(c.getOwnerId(), "дача", h.getLevel().dimension(), marker, 1);
        var result = CompanionEvents.executeAction(player(h, c), "go_to_place", "даче", 1);
        h.assertTrue(result.success(), "The walk was refused: " + result.message());
        h.assertTrue(c.workPhase().startsWith("PLACE"), "Not the place walk: " + c.workPhase());
        h.succeedWhen(() -> {
            h.assertTrue(!c.isGatheringTask() && !c.isWorking(), "The walk is still running: " + c.getLastTaskResult());
            h.assertTrue(c.blockPosition().distSqr(marker) <= 4 * 4,
                    "He did not arrive: " + c.blockPosition() + " marker " + marker + " " + c.getLastTaskResult());
            h.assertTrue(c.getLastTaskResult().contains("Пришёл к метке «дача»"), "No honest arrival: " + c.getLastTaskResult());
        });
    }

    /** A marker in the Nether while he is in the overworld: said as it is, no walk and no timeout. */
    @GameTest(template = "empty")
    public static void aMarkerInAnotherDimensionIsAnsweredHonestly(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        BlockPos marker = h.absolutePos(new BlockPos(40, 64, 40));
        CompanionMarkers.get(h.getLevel().getServer()).add(c.getOwnerId(), "портал", Level.NETHER, marker, 1);
        var result = CompanionEvents.executeAction(player(h, c), "go_to_place", "порталу", 1);
        h.assertTrue(!result.success(), "A walk into another dimension reported success");
        h.assertTrue(result.message().contains("в другом измерении") && result.message().contains("nether"),
                "The other dimension is not said: " + result.message());
        h.assertTrue(c.workPhase().isEmpty(), "He walked off into another dimension: " + c.workPhase());
        var listed = CompanionMarkerBoard.places(player(h, c));
        h.assertTrue(listed.size() == 1 && "nether".equals(listed.getFirst().world()), "The settings list lost the world: " + listed);
        h.succeed();
    }

    // ---- the settings list ----

    /** Renaming keeps the point and the id; a name another marker has is refused. */
    @GameTest(template = "empty")
    public static void renamingAMarkerKeepsItsPlaceAndRefusesDuplicates(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        var first = store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition(), 1).marker();
        var second = store.add(c.getOwnerId(), "колодец", h.getLevel().dimension(), c.blockPosition().offset(2, 0, 0), 2).marker();
        h.assertTrue(CompanionMarkerBoard.rename(owner, first.id(), "дачный участок"), "The rename was refused");
        var renamed = store.byId(c.getOwnerId(), first.id());
        h.assertTrue(renamed != null && renamed.name().equals("дачный участок") && renamed.pos().equals(first.pos()), "The rename moved it: " + renamed);
        h.assertTrue(!CompanionMarkerBoard.rename(owner, second.id(), "дачный участок"), "A duplicate name was taken");
        h.assertTrue(!CompanionMarkerBoard.rename(owner, second.id(), "а".repeat(CompanionMarkerNames.MAX + 1)), "A 25-character name was taken");
        h.assertTrue(!CompanionMarkerBoard.rename(owner, second.id(), "   "), "An empty name was taken");
        h.succeed();
    }

    /** Deleting one marker leaves its neighbour, and the list is what the screen shows. */
    @GameTest(template = "empty")
    public static void deletingAMarkerRemovesOnlyIt(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        var first = store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition(), 1).marker();
        store.add(c.getOwnerId(), "колодец", h.getLevel().dimension(), c.blockPosition().offset(2, 0, 0), 2);
        h.assertTrue(CompanionMarkerBoard.places(owner).size() == 2, "The list: " + CompanionMarkerBoard.places(owner));
        h.assertTrue(CompanionMarkerBoard.forget(owner, first.id()), "The delete was refused");
        var left = CompanionMarkerBoard.places(owner);
        h.assertTrue(left.size() == 1 && left.getFirst().name().equals("колодец"), "The neighbour went too: " + left);
        h.assertTrue(!CompanionMarkerBoard.forget(owner, first.id()), "A second delete reported success");
        h.succeed();
    }

    /** Coordinates are edited and clamped to the world border and the build height, not rejected. */
    @GameTest(template = "empty")
    public static void coordinatesAreClampedToTheWorldBorder(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        var marker = store.add(c.getOwnerId(), "далеко", h.getLevel().dimension(), c.blockPosition(), 1).marker();
        int far = 30_000_000;
        h.assertTrue(CompanionMarkerBoard.move(owner, marker.id(), far, 10_000, -far), "The move was refused");
        var moved = store.byId(c.getOwnerId(), marker.id());
        var border = h.getLevel().getWorldBorder();
        h.assertTrue(moved != null && moved.pos().getX() <= Math.ceil(border.getMaxX()) - 1 && moved.pos().getX() >= Math.floor(border.getMinX()),
                "X is outside the border: " + moved);
        h.assertTrue(moved.pos().getZ() <= Math.ceil(border.getMaxZ()) - 1 && moved.pos().getZ() >= Math.floor(border.getMinZ()),
                "Z is outside the border: " + moved);
        h.assertTrue(moved.pos().getY() == h.getLevel().getMaxBuildHeight() - 1, "Y is not clamped to the build height: " + moved);
        h.assertTrue(CompanionMarkerBoard.move(owner, marker.id(), 10, 64, 12), "A plain move was refused");
        h.assertTrue(store.byId(c.getOwnerId(), marker.id()).pos().equals(new BlockPos(10, 64, 12)), "The plain move changed: " + store.byId(c.getOwnerId(), marker.id()));
        h.succeed();
    }

    /** «Добавить здесь»: the player's own point becomes a marker. */
    @GameTest(template = "empty")
    public static void thePlayersCurrentPositionBecomesAMarker(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        BlockPos at = h.absolutePos(new BlockPos(20, 1, 24));
        owner.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        var added = CompanionMarkerBoard.addHere(owner, "лагерь");
        h.assertTrue(added != null && added.place().name().equals("лагерь"), "Not added: " + added);
        h.assertTrue(added.place().x() == at.getX() && added.place().y() == at.getY() && added.place().z() == at.getZ(), "Not at the player: " + added);
        h.assertTrue("world".equals(added.place().world()), "Wrong world name: " + added.place().world());
        h.assertTrue(added.message().contains("Запомнил место «лагерь»"), "No honest line: " + added.message());
        h.assertTrue(CompanionMarkerBoard.addHere(owner, "") == null, "An empty name was added");
        h.succeed();
    }

    /** Twenty markers are the limit: the twenty-first evicts the oldest and the answer names it. */
    @GameTest(template = "empty")
    public static void theTwentyFirstMarkerEvictsTheOldestWithAnHonestLine(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        for (int index = 1; index <= CompanionMarkers.MOST; index++)
            store.add(c.getOwnerId(), "метка" + index, h.getLevel().dimension(), c.blockPosition().offset(index, 0, 0), index);
        var result = CompanionEvents.executeAction(owner, "remember_place", "метка21", 1);
        h.assertTrue(result.success(), "The twenty-first marker was refused: " + result.message());
        h.assertTrue(result.message().contains("«метка1»") && result.message().contains("забыл"),
                "The evicted marker is not named: " + result.message());
        var markers = store.all(c.getOwnerId());
        h.assertTrue(markers.size() == CompanionMarkers.MOST, "The cap is not kept: " + markers.size());
        h.assertTrue(markers.stream().noneMatch(marker -> marker.name().equals("метка1")), "The oldest stayed: " + markers);
        h.assertTrue(markers.stream().anyMatch(marker -> marker.name().equals("метка21")), "The new one is missing: " + markers);
        h.succeed();
    }

    // ---- persistence and old worlds ----

    /** Markers survive a save and load: id, name, dimension, point and time come back the same. */
    @GameTest(template = "empty")
    public static void markersSurviveASaveAndLoad(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        var server = h.getLevel().getServer();
        var store = CompanionMarkers.get(server);
        var marker = store.add(c.getOwnerId(), "железный рудник", h.getLevel().dimension(), new BlockPos(12, 64, -30), 12345).marker();
        var saved = store.save(new CompoundTag(), server.registryAccess());
        var loaded = CompanionMarkers.load(saved, server.registryAccess());
        var markers = loaded.all(c.getOwnerId());
        h.assertTrue(markers.size() == 1, "The marker did not survive: " + markers);
        var back = markers.getFirst();
        h.assertTrue(back.id().equals(marker.id()) && back.name().equals(marker.name()) && back.pos().equals(marker.pos())
                && back.dimension().equals(marker.dimension()) && back.created() == marker.created(), "The marker came back changed: " + back);
        // Another player's list is his own: nothing leaks into it.
        h.assertTrue(loaded.all(java.util.UUID.randomUUID()).isEmpty(), "Another owner has markers");
        h.succeed();
    }

    /** A world saved before markers: no data, no crash, an empty list and the switch on. */
    @GameTest(template = "empty")
    public static void anOldWorldWithoutMarkersLoadsCleanly(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        var server = h.getLevel().getServer();
        var loaded = CompanionMarkers.load(new CompoundTag(), server.registryAccess());
        h.assertTrue(loaded.all(c.getOwnerId()).isEmpty(), "An empty save has markers: " + loaded.all(c.getOwnerId()));
        var half = new CompoundTag();
        half.put("owners", new CompoundTag());
        h.assertTrue(CompanionMarkers.load(half, server.registryAccess()).all(c.getOwnerId()).isEmpty(), "An empty owners tag has markers");
        // The companion's switch is a setting, not saved data: an old entity loads with it on.
        c.readAdditionalSaveData(new CompoundTag());
        h.assertTrue(c.markersEnabled(), "An old companion loaded with markers off");
        h.succeed();
    }

    // ---- the switch ----

    /** Switched off: no recognition, no remembering, no walks; the settings list is still readable. */
    @GameTest(template = "empty")
    public static void switchedOffThereIsNoRecognitionAndNoTrip(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        CompanionMarkers.get(h.getLevel().getServer()).add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition().offset(6, 0, 0), 1);
        c.setMarkersEnabled(false);
        h.assertTrue(CompanionPlaceSpeech.parse("иди к даче", "Олег", List.of("дача"), false).kind() == CompanionPlaceSpeech.Kind.NONE,
                "A switched-off feature recognised a name");
        h.assertTrue(CompanionLocalCommands.route("иди к даче", "Олег", List.of("дача"), false) == null,
                "A switched-off feature started a trip");
        var walked = CompanionEvents.executeAction(owner, "go_to_place", "дача", 1);
        h.assertTrue(!walked.success() && walked.message().contains("выключены"), "A trip with the switch off: " + walked.message());
        h.assertTrue(c.workPhase().isEmpty(), "A walk started with the switch off: " + c.workPhase());
        var remembered = CompanionEvents.executeAction(owner, "remember_place", "колодец", 1);
        h.assertTrue(!remembered.success() && remembered.message().contains("выключены"), "A marker was saved with the switch off: " + remembered.message());
        h.assertTrue(CompanionMarkerBoard.places(owner).size() == 1, "The list is not readable with the switch off");
        h.succeed();
    }

    // ---- the codec and the plan ----

    /** Both actions are accepted with a name; empty, long, coordinate and unknown-field steps spoil the plan. */
    @GameTest(template = "empty")
    public static void theCodecTakesBothActionsAndRefusesBadOnes(GameTestHelper h) {
        var remembered = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"remember_place\",\"item\":\"дача\"}]"));
        h.assertTrue(remembered.size() == 1 && "remember_place".equals(remembered.getFirst().type()) && "дача".equals(remembered.getFirst().item()),
                "remember_place: " + remembered);
        var went = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"go_to_place\",\"item\":\"железный рудник\"}]"));
        h.assertTrue(went.size() == 1 && "go_to_place".equals(went.getFirst().type()) && "железный рудник".equals(went.getFirst().item()),
                "go_to_place: " + went);
        String longest = "а".repeat(CompanionMarkerNames.MAX);
        var longestPlan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"go_to_place\",\"item\":\"" + longest + "\"}]"));
        h.assertTrue(longestPlan.size() == 1 && longest.equals(longestPlan.getFirst().item()), "A 24-character name was refused");
        for (String bad : List.of(
                "[{\"action\":\"remember_place\",\"item\":\"\"}]",
                "[{\"action\":\"remember_place\",\"item\":\"" + "а".repeat(CompanionMarkerNames.MAX + 1) + "\"}]",
                "[{\"action\":\"go_to_place\",\"item\":\"100, 64, 200\"}]",
                "[{\"action\":\"go_to_place\",\"item\":\"дача\",\"count\":2}]",
                "[{\"action\":\"go_to_place\",\"item\":\"дача\",\"location\":\"inside_house\"}]",
                "[{\"action\":\"fly_to_place\",\"item\":\"дача\"}]",
                "[{\"action\":\"remember_place\"}]")) {
            boolean refused = false;
            try { CompanionPlanCodec.parsePlan(JsonParser.parseString(bad)); } catch (RuntimeException expected) { refused = true; }
            h.assertTrue(refused, "Accepted: " + bad);
        }
        h.assertTrue(CompanionPlanCodec.ACTIONS.contains("remember_place") && CompanionPlanCodec.ACTIONS.contains("go_to_place"),
                "The actions are not offered to the model");
        h.succeed();
    }

    /** remember_place then go_to_place in one plan: both run in order, and the walk is waited for by its work id. */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void thePlanRunsRememberThenGoInOrder(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        var plan = CompanionPlanRunner.run(List.of(
                        new PlannedAction("remember_place", 0, 1, "дача"),
                        new PlannedAction("go_to_place", 0, 1, "дача")),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-marker-plan", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            int saved = outcome.report().indexOf("Запомнил место «дача»");
            int arrived = outcome.report().indexOf("Пришёл к метке «дача»");
            h.assertTrue(saved >= 0 && arrived > saved, "Not in order: " + outcome.report());
            h.assertTrue(outcome.skipped().isEmpty() && !outcome.report().contains("Не выполнено"), "A step was skipped: " + outcome.report());
            h.assertTrue(CompanionMarkers.get(h.getLevel().getServer()).all(c.getOwnerId()).size() == 1,
                    "The marker is missing: " + CompanionMarkers.get(h.getLevel().getServer()).all(c.getOwnerId()));
        });
    }

    /** The runner's busy retry applies to the walk like any other work order; remembering is instantaneous. */
    @GameTest(template = "empty")
    public static void theRunnerTreatsTheWalkAsWork(GameTestHelper h) {
        h.assertTrue(CompanionPlanRunner.WORK.contains("go_to_place"), "go_to_place is not an order");
        h.assertTrue(!CompanionPlanRunner.WORK.contains("remember_place"), "remember_place is not instantaneous");
        h.assertTrue(CompanionServerPlanPort.tracksWork("go_to_place"), "The port does not track the walk");
        h.assertTrue(!CompanionServerPlanPort.tracksWork("remember_place"), "The port tracks an instantaneous step");
        h.succeed();
    }

    /** A plan whose walk names no known marker: the honest answer is the plan's answer, and no trip starts. */
    @GameTest(template = "empty")
    public static void thePlanAnswersAnUnknownMarkerHonestly(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        CompanionMarkers.get(h.getLevel().getServer()).add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition().offset(4, 0, 0), 1);
        var plan = CompanionPlanRunner.run(List.of(new PlannedAction("go_to_place", 0, 1, "озеро")),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-marker-unknown", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            h.assertTrue(outcome.report().contains("Не знаю метки «озеро»") && outcome.report().contains("«дача»"),
                    "No honest answer in the plan: " + outcome.report());
            h.assertTrue(outcome.recoveries() == 0 && !outcome.report().contains("Повтор шага"),
                    "An unknown name was retried: " + outcome.report());
            h.assertTrue(c.workPhase().isEmpty(), "A trip started for an unknown name: " + c.workPhase());
        });
    }

    // ---- fixtures ----

    private static void arena(GameTestHelper h) {
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 8);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(5, 1, 8));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        companion.setFollowing(false);
        // The world's store outlives a test: this owner starts with no markers of his own.
        var store = CompanionMarkers.get(h.getLevel().getServer());
        for (var marker : store.all(owner.getUUID())) store.remove(owner.getUUID(), marker.id());
        return companion;
    }

    private static ServerPlayer player(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
}

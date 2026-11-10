package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionMarkerView;
import dev.aicompanion.client.CompanionParkHold;
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
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

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

    /** A marker is saved where the player stands, and his absence is said honestly instead of pretending it is his point. */
    @GameTest(template = "empty")
    public static void theMarkerIsSavedAtTheOwnersPoint(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var result = CompanionEvents.executeAction(owner, "remember_place", "колодец", 1);
        h.assertTrue(result.success(), "The marker was refused: " + result.message());
        h.assertTrue(result.message().contains("Запомнил место «колодец»"), "No honest answer: " + result.message());
        h.assertTrue(!result.message().contains("стою я"), "An online owner's point was called the companion's: " + result.message());
        var markers = CompanionMarkers.get(h.getLevel().getServer()).all(c.getOwnerId());
        h.assertTrue(markers.size() == 1, "Not saved: " + markers);
        var marker = markers.getFirst();
        h.assertTrue(marker.pos().equals(owner.blockPosition()), "Saved elsewhere: " + marker.pos() + " owner at " + owner.blockPosition());
        h.assertTrue(marker.dimension().equals(owner.level().dimension()), "Another dimension: " + marker.dimension());
        h.assertTrue(marker.created() > 0, "No creation time: " + marker.created());
        // With no owner online there is no player's point: his own feet are saved, and the answer says so.
        UUID away = UUID.randomUUID();
        c.setOwnerId(away);
        var offline = c.rememberPlace("дом");
        h.assertTrue(offline.success() && offline.message().contains("стою я"), "The absent owner was not said: " + offline.message());
        var own = CompanionMarkers.get(h.getLevel().getServer()).all(away);
        h.assertTrue(own.size() == 1 && own.getFirst().pos().equals(c.blockPosition()),
                "The companion's own point was not saved: " + own + " he at " + c.blockPosition());
        CompanionMarkers.get(h.getLevel().getServer()).remove(away, own.getFirst().id());
        h.succeed();
    }

    /** The same name again moves that one marker: its id and creation time stay, no second marker is made. */
    @GameTest(template = "empty")
    public static void theSameNameMovesTheMarkerInsteadOfAddingASecond(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        BlockPos first = c.blockPosition().offset(2, 0, 0);
        var saved = store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), first, 100).marker();
        BlockPos second = c.blockPosition().offset(5, 0, 0);
        var again = store.add(c.getOwnerId(), "ДАЧА", h.getLevel().dimension(), second, 200);
        h.assertTrue(again.moved(), "The same name made a second marker: " + again);
        h.assertTrue(again.evicted() == null, "A move evicted a marker: " + again.evicted());
        h.assertTrue(again.marker().id().equals(saved.id()), "The move changed the id: " + again.marker());
        h.assertTrue(again.marker().pos().equals(second), "The marker did not move: " + again.marker());
        h.assertTrue(again.marker().created() == 100, "The move changed the creation time: " + again.marker());
        h.assertTrue(store.all(c.getOwnerId()).size() == 1, "Two markers carry one name: " + store.all(c.getOwnerId()));
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

    /** Names with digits and spaces are ordinary names: «дом2», «шахта 3», «железный рудник» resolve like any word. */
    @GameTest(template = "empty")
    public static void namesWithDigitsAndSpacesResolve(GameTestHelper h) {
        List<String> none = List.of();
        h.assertTrue("companion place remember дом2".equals(CompanionLocalCommands.route("запомни это дом2", "Олег", none, true)),
                "«запомни это дом2»: " + CompanionLocalCommands.route("запомни это дом2", "Олег", none, true));
        h.assertTrue("companion place go дом2".equals(CompanionLocalCommands.route("иди к дому2", "Олег", List.of("дом2"), true)),
                "«иди к дому2»: " + CompanionLocalCommands.route("иди к дому2", "Олег", List.of("дом2"), true));
        h.assertTrue("companion place go шахта 3".equals(CompanionLocalCommands.route("иди к шахте 3", "Олег", List.of("шахта 3"), true)),
                "«иди к шахте 3»: " + CompanionLocalCommands.route("иди к шахте 3", "Олег", List.of("шахта 3"), true));
        h.assertTrue("companion place go железный рудник".equals(CompanionLocalCommands.route("иди к железному руднику", "Олег", List.of("железный рудник"), true)),
                "«иди к железному руднику»: " + CompanionLocalCommands.route("иди к железному руднику", "Олег", List.of("железный рудник"), true));
        // At run time: remembering the name and walking to it.
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var remembered = CompanionEvents.executeAction(owner, "remember_place", "дом2", 1);
        h.assertTrue(remembered.success(), "«дом2» was refused: " + remembered.message());
        var markers = CompanionMarkers.get(h.getLevel().getServer()).all(c.getOwnerId());
        h.assertTrue(markers.size() == 1 && markers.getFirst().name().equals("дом2"), "The name lost its digit: " + markers);
        var walked = CompanionEvents.executeAction(owner, "go_to_place", "дому2", 1);
        h.assertTrue(walked.success(), "«иди к дому2» was refused: " + walked.message());
        h.assertTrue(c.workPhase().startsWith("PLACE"), "Not the place walk: " + c.workPhase());
        h.succeed();
    }

    /** A coordinate triple is not a name: one question, and no marker is made. */
    @GameTest(template = "empty")
    public static void aCoordinateTripleAsksOneQuestionInsteadOfBecomingAMarker(GameTestHelper h) {
        var speech = CompanionPlaceSpeech.remember("запомни это место, 100, 64, 200", "Олег", true);
        h.assertTrue(speech.kind() == CompanionPlaceSpeech.Kind.COORDINATES, "A coordinate triple was taken: " + speech.kind());
        h.assertTrue(speech.question() != null && speech.question().contains("координаты"), "No honest question: " + speech.question());
        h.assertTrue(speech.question().chars().filter(ch -> ch == '?').count() == 1, "Not one question: " + speech.question());
        h.assertTrue(CompanionLocalCommands.route("запомни это место, 100, 64, 200", "Олег", List.of(), true) == null,
                "The coordinate triple became a command");
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var result = CompanionEvents.executeAction(owner, "remember_place", "100, 64, 200", 1);
        h.assertTrue(!result.success() && result.message().contains("координаты"), "The server took the coordinates: " + result.message());
        h.assertTrue(CompanionMarkers.get(h.getLevel().getServer()).all(c.getOwnerId()).isEmpty(), "A coordinate marker was saved");
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

    /** Matching is per owner: each player's names resolve only in his own list. */
    @GameTest(template = "empty")
    public static void matchingIsPerOwner(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer ownerB = h.makeMockServerPlayerInLevel();
        var store = CompanionMarkers.get(h.getLevel().getServer());
        store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition(), 1);
        store.add(ownerB.getUUID(), "колодец", h.getLevel().dimension(), c.blockPosition().offset(2, 0, 0), 2);
        h.assertTrue(store.matching(c.getOwnerId(), "даче").size() == 1, "The owner lost his own name: " + store.matching(c.getOwnerId(), "даче"));
        h.assertTrue(store.matching(c.getOwnerId(), "колодцу").isEmpty(), "The owner saw another list: " + store.matching(c.getOwnerId(), "колодцу"));
        h.assertTrue(store.matching(ownerB.getUUID(), "колодцу").size() == 1, "The other owner lost his name: " + store.matching(ownerB.getUUID(), "колодцу"));
        h.assertTrue(store.matching(ownerB.getUUID(), "даче").isEmpty(), "The other owner saw the first list: " + store.matching(ownerB.getUUID(), "даче"));
        // The same name in both lists is two independent markers.
        store.add(ownerB.getUUID(), "дача", h.getLevel().dimension(), c.blockPosition().offset(4, 0, 0), 3);
        h.assertTrue(store.matching(c.getOwnerId(), "даче").size() == 1 && store.matching(ownerB.getUUID(), "даче").size() == 1,
                "One name in two lists: " + store.matching(c.getOwnerId(), "даче") + " / " + store.matching(ownerB.getUUID(), "даче"));
        h.succeed();
    }

    /** Another player cannot see or change a marker that is not his. */
    @GameTest(template = "empty")
    public static void anotherOwnerCannotSeeOrChangeTheMarker(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer ownerB = h.makeMockServerPlayerInLevel();
        var store = CompanionMarkers.get(h.getLevel().getServer());
        var marker = store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition().offset(3, 0, 0), 1).marker();
        h.assertTrue(CompanionMarkerBoard.places(ownerB).isEmpty(), "The screen shows another owner's marker: " + CompanionMarkerBoard.places(ownerB));
        h.assertTrue(store.byId(ownerB.getUUID(), marker.id()) == null, "Another owner found the marker by id");
        h.assertTrue(!CompanionMarkerBoard.rename(ownerB, marker.id(), "чужая"), "Another owner renamed the marker");
        h.assertTrue(!CompanionMarkerBoard.move(ownerB, marker.id(), 1, 2, 3), "Another owner moved the marker");
        h.assertTrue(!CompanionMarkerBoard.forget(ownerB, marker.id()), "Another owner deleted the marker");
        var left = store.all(c.getOwnerId());
        h.assertTrue(left.size() == 1 && left.getFirst().name().equals("дача") && left.getFirst().pos().equals(marker.pos()),
                "The marker changed under another owner: " + left);
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

    /** A marker moved three blocks above him: an honest «нет пути», and the plan ends instead of walking forever. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void aMarkerAboveHisHeadEndsWithAnHonestNoPath(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        var marker = store.add(c.getOwnerId(), "высоко", h.getLevel().dimension(), c.blockPosition(), 1).marker();
        BlockPos above = c.blockPosition().offset(2, 3, 0);
        h.assertTrue(CompanionMarkerBoard.move(owner, marker.id(), above.getX(), above.getY(), above.getZ()), "The move was refused");
        var plan = CompanionPlanRunner.run(List.of(new PlannedAction("go_to_place", 0, 1, "высоко")),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-marker-above", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The walk to the marker above never ended: " + c.getLastTaskResult());
            var outcome = plan.join();
            h.assertTrue(outcome.report().contains("нет пути") && outcome.report().contains("«высоко»"),
                    "No honest no-path answer: " + outcome.report());
            h.assertTrue(outcome.recoveries() == 0 && !outcome.report().contains("Повтор шага"),
                    "The no-path walk was retried: " + outcome.report());
        });
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

    /** The screen's save: a refused rename leaves the coordinates as they were; an accepted one moves the marker. */
    @GameTest(template = "empty")
    public static void aRefusedRenameDoesNotMoveTheMarker(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition(), 1);
        var second = store.add(c.getOwnerId(), "колодец", h.getLevel().dimension(), c.blockPosition().offset(2, 0, 0), 2).marker();
        BlockPos wanted = c.blockPosition().offset(6, 0, 3);
        var refused = CompanionMarkerBoard.save(owner, second.id(), "дача", wanted.getX(), wanted.getY(), wanted.getZ());
        h.assertTrue(!refused.renamed() && !refused.moved(), "A duplicate rename reported a save: " + refused);
        var unchanged = store.byId(c.getOwnerId(), second.id());
        h.assertTrue(unchanged.name().equals("колодец") && unchanged.pos().equals(second.pos()),
                "A refused rename still moved the marker: " + unchanged);
        var accepted = CompanionMarkerBoard.save(owner, second.id(), "колодец2", wanted.getX(), wanted.getY(), wanted.getZ());
        h.assertTrue(accepted.renamed() && accepted.moved(), "An accepted save was refused: " + accepted);
        var saved = store.byId(c.getOwnerId(), second.id());
        h.assertTrue(saved.name().equals("колодец2") && saved.pos().equals(wanted), "The accepted save did not land: " + saved);
        h.succeed();
    }

    /** At the cap, renaming an existing marker changes it in place: nothing is evicted. */
    @GameTest(template = "empty")
    public static void renamingAtTheCapDoesNotEvict(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        for (int index = 1; index <= CompanionMarkers.MOST; index++)
            store.add(c.getOwnerId(), "метка" + index, h.getLevel().dimension(), c.blockPosition().offset(index, 0, 0), index);
        var oldest = store.all(c.getOwnerId()).getFirst();
        h.assertTrue(CompanionMarkerBoard.rename(owner, oldest.id(), "первая"), "The rename at the cap was refused");
        var after = store.all(c.getOwnerId());
        h.assertTrue(after.size() == CompanionMarkers.MOST, "The rename changed the count: " + after.size());
        h.assertTrue(after.stream().anyMatch(marker -> marker.id().equals(oldest.id()) && marker.name().equals("первая")),
                "The renamed marker is missing: " + after);
        h.assertTrue(after.stream().noneMatch(marker -> marker.name().equals("метка1")), "The old name stayed: " + after);
        h.assertTrue(after.stream().anyMatch(marker -> marker.name().equals("метка20")), "The newest marker was evicted: " + after);
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

    /** The chat's recognition copy follows every settings change at once, and pull reads the list on the server thread. */
    @GameTest(template = "empty")
    public static void theRecognitionCopyFollowsTheServerList(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        var added = CompanionMarkerBoard.addHere(owner, "дача");
        h.assertTrue(added != null, "The marker was refused");
        h.assertTrue(CompanionMarkerView.names().contains("дача"), "The new name did not reach recognition: " + CompanionMarkerView.names());
        var marker = store.all(c.getOwnerId()).getFirst();
        h.assertTrue(CompanionMarkerBoard.rename(owner, marker.id(), "дача2"), "The rename was refused");
        h.assertTrue(CompanionMarkerView.names().contains("дача2") && !CompanionMarkerView.names().contains("дача"),
                "The rename did not reach recognition: " + CompanionMarkerView.names());
        h.assertTrue(CompanionMarkerBoard.forget(owner, marker.id()), "The delete was refused");
        h.assertTrue(!CompanionMarkerView.names().contains("дача2"), "The delete did not reach recognition: " + CompanionMarkerView.names());
        h.assertTrue(CompanionMarkerBoard.addHere(owner, "колодец") != null, "The second marker was refused");
        boolean[] pulled = {false};
        CompanionMarkerView.pull(owner, true, (names, on) -> pulled[0] = names.contains("колодец") && on);
        h.succeedWhen(() -> h.assertTrue(pulled[0], "pull did not deliver the names on the server thread: " + CompanionMarkerView.names()));
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

    /** Corrupt entries in a saved list are skipped: the good marker survives and nothing throws. */
    @GameTest(template = "empty")
    public static void loadSkipsCorruptMarkersWithoutThrowing(GameTestHelper h) {
        var server = h.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        ListTag list = new ListTag();
        list.add(markerTag(UUID.randomUUID(), "дача", "minecraft:overworld", 1, 64, 2, 5));
        list.add(markerTag(UUID.randomUUID(), "", "minecraft:overworld", 0, 0, 0, 6));
        list.add(markerTag(UUID.randomUUID(), "а".repeat(CompanionMarkerNames.MAX + 1), "minecraft:overworld", 0, 0, 0, 7));
        CompoundTag noDimension = new CompoundTag();
        noDimension.putUUID("id", UUID.randomUUID());
        noDimension.putString("name", "шахта");
        noDimension.putLong("created", 8);
        list.add(noDimension);
        CompoundTag owners = new CompoundTag();
        owners.put(owner.toString(), list);
        CompoundTag tag = new CompoundTag();
        tag.put("owners", owners);
        var loaded = CompanionMarkers.load(tag, server.registryAccess());
        var markers = loaded.all(owner);
        h.assertTrue(markers.size() == 1, "The corrupt entries were not skipped: " + markers);
        h.assertTrue(markers.getFirst().name().equals("дача") && markers.getFirst().pos().equals(new BlockPos(1, 64, 2)),
                "The good marker came back changed: " + markers.getFirst());
        h.succeed();
    }

    /** A bad owner key, a bad dimension and a 25-character name are skipped without throwing. */
    @GameTest(template = "empty")
    public static void loadSkipsBadOwnerKeysDimensionsAndLongNames(GameTestHelper h) {
        var server = h.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        ListTag badEntries = new ListTag();
        badEntries.add(markerTag(UUID.randomUUID(), "колодец", "not a location", 2, 64, 3, 9));
        badEntries.add(markerTag(UUID.randomUUID(), "а".repeat(CompanionMarkerNames.MAX + 1), "minecraft:overworld", 2, 64, 3, 10));
        CompoundTag owners = new CompoundTag();
        owners.put("not-a-uuid", badEntries);
        owners.put(owner.toString(), badEntries);
        CompoundTag tag = new CompoundTag();
        tag.put("owners", owners);
        var loaded = CompanionMarkers.load(tag, server.registryAccess());
        h.assertTrue(loaded.all(owner).isEmpty(), "A bad entry survived: " + loaded.all(owner));
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

    /** Switched back on, the same names are recognised and walked to again. */
    @GameTest(template = "empty")
    public static void switchingBackOnRestoresWalking(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        CompanionMarkers.get(h.getLevel().getServer()).add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition().offset(6, 0, 0), 1);
        c.setMarkersEnabled(false);
        h.assertTrue(!CompanionEvents.executeAction(owner, "go_to_place", "даче", 1).success(), "The walk ran with the switch off");
        c.setMarkersEnabled(true);
        h.assertTrue("companion place go дача".equals(CompanionLocalCommands.route("иди к даче", "Олег", List.of("дача"), true)),
                "The phrase is not recognised after switching on");
        var walked = CompanionEvents.executeAction(owner, "go_to_place", "даче", 1);
        h.assertTrue(walked.success(), "The walk was refused after switching on: " + walked.message());
        h.assertTrue(c.workPhase().startsWith("PLACE"), "Not the place walk: " + c.workPhase());
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

    /** The local command becomes the plan step: remembering keeps the name, going keeps the name as it was said. */
    @GameTest(template = "empty")
    public static void placeActionParsesRememberAndGo(GameTestHelper h) {
        var remember = CompanionLocalCommands.action("companion place remember железный рудник");
        h.assertTrue("remember_place".equals(remember.type()) && "железный рудник".equals(remember.item()) && remember.count() == 1,
                "remember_place step: " + remember);
        var go = CompanionLocalCommands.action("companion place go дом2");
        h.assertTrue("go_to_place".equals(go.type()) && "дом2".equals(go.item()) && go.count() == 1, "go_to_place step: " + go);
        var rememberPlan = CompanionLocalCommands.actions("companion place remember дача");
        h.assertTrue(rememberPlan.size() == 1 && "remember_place".equals(rememberPlan.getFirst().type()),
                "The remember plan is not one step: " + rememberPlan);
        h.succeed();
    }

    /** A named place neither gives nor takes an item: a parked smelt does not hold it back. */
    @GameTest(template = "empty")
    public static void aNamedPlaceDoesNotWaitForAParkedSmelt(GameTestHelper h) {
        var parked = List.of(new CompanionParkHold.Parked("minecraft:raw_iron", "minecraft:iron_ingot"));
        h.assertTrue(!CompanionParkHold.held(List.of(new PlannedAction("go_to_place", 0, 1, "дача")), parked),
                "The walk waited for the parked smelt");
        h.assertTrue(!CompanionParkHold.held(List.of(new PlannedAction("remember_place", 0, 1, "дача")), parked),
                "Remembering waited for the parked smelt");
        // Control: a hand-over of the parked product still waits.
        h.assertTrue(CompanionParkHold.held(List.of(new PlannedAction("give_items", 0, 1, "minecraft:iron_ingot")), parked),
                "A product hand-over stopped waiting");
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

    /**
     * The runner waits for the walk itself: the plan stays running while he is still on his way and reports the arrival
     * only after it, rather than treating the walk as done when it is dispatched.
     */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void theRunnerWaitsForTheWalkBeforeReportingIt(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        BlockPos marker = h.absolutePos(new BlockPos(22, 1, 20));
        CompanionMarkers.get(h.getLevel().getServer()).add(c.getOwnerId(), "дача", h.getLevel().dimension(), marker, 1);
        var plan = CompanionPlanRunner.run(List.of(new PlannedAction("go_to_place", 0, 1, "дача")),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-marker-waits", () -> false));
        // Five ticks in, the walk is still going: a plan that does not wait would already have reported it done.
        h.runAfterDelay(5, () -> {
            h.assertTrue(!plan.isDone(), "The plan reported the walk done while he was still walking: " + c.getLastTaskResult());
            h.assertTrue(c.workPhase().startsWith("PLACE"), "The walk is not the running work: " + c.workPhase());
            h.assertTrue(c.blockPosition().distSqr(marker) > 4 * 4, "He arrived before the plan was checked: " + c.blockPosition());
        });
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            h.assertTrue(outcome.report().contains("Пришёл к метке «дача»"), "No arrival in the plan: " + outcome.report());
            h.assertTrue(c.blockPosition().distSqr(marker) <= 4 * 4, "He did not arrive: " + c.blockPosition() + " marker " + marker);
        });
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

    /** A plan step whose marker is in another dimension: said honestly, and not retried. */
    @GameTest(template = "empty")
    public static void aCrossDimensionPlanStepIsNotRetried(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        CompanionMarkers.get(h.getLevel().getServer()).add(c.getOwnerId(), "портал", Level.NETHER, h.absolutePos(new BlockPos(40, 64, 40)), 1);
        var plan = CompanionPlanRunner.run(List.of(new PlannedAction("go_to_place", 0, 1, "порталу")),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-marker-nether-plan", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            h.assertTrue(outcome.report().contains("в другом измерении") && outcome.report().contains("nether"),
                    "The other dimension is not said: " + outcome.report());
            h.assertTrue(outcome.recoveries() == 0 && !outcome.report().contains("Повтор шага"),
                    "The cross-dimension step was retried: " + outcome.report());
            h.assertTrue(c.workPhase().isEmpty(), "He walked off into another dimension: " + c.workPhase());
        });
    }

    // ---- fixtures ----

    private static void arena(GameTestHelper h) {
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
    }

    /** One saved marker as the store writes it, for the load tests. */
    private static CompoundTag markerTag(UUID id, String name, String dimension, int x, int y, int z, long created) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putString("dimension", dimension);
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        tag.putLong("created", created);
        return tag;
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

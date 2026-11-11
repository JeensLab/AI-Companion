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
 * Russian word forms, one honest question for an unknown or ambiguous name, «забудь дачу» taking one or every marker off
 * the list, a real walk by the normal path, another dimension said as it is, renaming, deleting, coordinates, the cap and
 * its eviction, persistence, the settings switch, and the two plan actions with their codec and runner wiring.
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

    /** A name written straight into the store is checked like a rename: an over-long, empty or coordinate name is refused. */
    @GameTest(template = "empty")
    public static void anOverLongNameWrittenDirectlyIsRejected(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        String longest = "а".repeat(CompanionMarkerNames.MAX);
        h.assertTrue(store.add(c.getOwnerId(), longest, h.getLevel().dimension(), c.blockPosition(), 1).marker() != null,
                "A name of exactly the limit was refused");
        var tooLong = store.add(c.getOwnerId(), longest + "б", h.getLevel().dimension(), c.blockPosition().offset(2, 0, 0), 2);
        h.assertTrue(tooLong.refused(), "A 25-character name was stored: " + tooLong);
        h.assertTrue(tooLong.refusal().contains("до " + CompanionMarkerNames.MAX + " символов"), "No honest line: " + tooLong.refusal());
        var coordinates = store.add(c.getOwnerId(), "100, 64, 200", h.getLevel().dimension(), c.blockPosition(), 3);
        h.assertTrue(coordinates.refused() && coordinates.refusal().contains("координаты"), "A coordinate name was stored: " + coordinates);
        var empty = store.add(c.getOwnerId(), "   ", h.getLevel().dimension(), c.blockPosition(), 4);
        h.assertTrue(empty.refused() && empty.refusal().contains("пустым"), "An empty name was stored: " + empty);
        var stored = store.all(c.getOwnerId());
        h.assertTrue(stored.size() == 1 && stored.getFirst().name().equals(longest), "A refused name was stored: " + stored);
        // Nothing was written, so the reload has nothing to drop either.
        var server = h.getLevel().getServer();
        var loaded = CompanionMarkers.load(store.save(new CompoundTag(), server.registryAccess()), server.registryAccess());
        h.assertTrue(loaded.all(c.getOwnerId()).size() == 1 && loaded.all(c.getOwnerId()).getFirst().name().equals(longest),
                "The reload lost or gained a marker: " + loaded.all(c.getOwnerId()));
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

    /** «Охраняй меня» is no marker (the word points at a person), and «до дома» without a marker stays the model's, not a question. */
    @GameTest(template = "empty")
    public static void peopleAndTheHomeWordAreNotMarkers(GameTestHelper h) {
        String guard = CompanionLocalCommands.route("охраняй меня", "Олег", List.of("дача"), true);
        h.assertTrue(guard == null || !guard.startsWith("companion place"),
                "«охраняй меня» became a place order: " + guard);
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

    /** «сад и огород», «дом и баня»: a name the screen accepts holds «и», and speech resolves it too. */
    @GameTest(template = "empty")
    public static void namesWithConjunctionsRememberAndWalk(GameTestHelper h) {
        List<String> none = List.of();
        h.assertTrue("companion place remember сад и огород".equals(CompanionLocalCommands.route("запомни это сад и огород", "Олег", none, true)),
                "«запомни это сад и огород»: " + CompanionLocalCommands.route("запомни это сад и огород", "Олег", none, true));
        h.assertTrue("companion place remember дом и баня".equals(CompanionLocalCommands.route("запомни это место, дом и баня", "Олег", none, true)),
                "«запомни это место, дом и баня»: " + CompanionLocalCommands.route("запомни это место, дом и баня", "Олег", none, true));
        h.assertTrue("companion place go сад и огород".equals(CompanionLocalCommands.route("иди к саду и огороду", "Олег", List.of("сад и огород"), true)),
                "«иди к саду и огороду»: " + CompanionLocalCommands.route("иди к саду и огороду", "Олег", List.of("сад и огород"), true));
        h.assertTrue("companion place go дом и баня".equals(CompanionLocalCommands.route("веди меня к дому и бане", "Олег", List.of("сад и огород", "дом и баня"), true)),
                "«веди меня к дому и бане»: " + CompanionLocalCommands.route("веди меня к дому и бане", "Олег", List.of("сад и огород", "дом и баня"), true));
        // A request with steps still stays the model's: only a name the place parse resolved wins over «и»/«потом».
        h.assertTrue(CompanionLocalCommands.route("иди к озеру, потом домой", "Олег", List.of("дача"), true) == null,
                "A request with steps became a walk");
        // At run time: the name is remembered by speech and walked to.
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var remembered = CompanionEvents.executeAction(owner, "remember_place", "сад и огород", 1);
        h.assertTrue(remembered.success(), "«сад и огород» was refused: " + remembered.message());
        var markers = CompanionMarkers.get(h.getLevel().getServer()).all(c.getOwnerId());
        h.assertTrue(markers.size() == 1 && markers.getFirst().name().equals("сад и огород"), "The name was cut: " + markers);
        var walked = CompanionEvents.executeAction(owner, "go_to_place", "саду и огороду", 1);
        h.assertTrue(walked.success(), "«иди к саду и огороду» was refused: " + walked.message());
        h.assertTrue(c.workPhase().startsWith("PLACE"), "Not the place walk: " + c.workPhase());
        h.succeed();
    }

    /**
     * «иди к даче, потом домой»: the name resolves, but the words left after it are a second leg, so the model plans
     * both; a name that holds «и» itself leaves nothing, and a phrase that only softens the order stays one step.
     */
    @GameTest(template = "empty")
    public static void aSecondLegAfterAKnownNameGoesToTheModel(GameTestHelper h) {
        List<String> markers = List.of("дача");
        h.assertTrue(CompanionLocalCommands.route("иди к даче, потом домой", "Олег", markers, true) == null,
                "Two legs became one walk: " + CompanionLocalCommands.route("иди к даче, потом домой", "Олег", markers, true));
        h.assertTrue(CompanionLocalCommands.route("веди меня к даче, затем домой", "Олег", markers, true) == null,
                "«затем» lost the second leg: " + CompanionLocalCommands.route("веди меня к даче, затем домой", "Олег", markers, true));
        // A comma separates a second place just as well: «домой» is no word that softens the order.
        h.assertTrue(CompanionLocalCommands.route("иди к даче, домой", "Олег", markers, true) == null,
                "A comma and a second place became one walk: " + CompanionLocalCommands.route("иди к даче, домой", "Олег", markers, true));
        h.assertTrue(CompanionLocalCommands.route("отнеси ресы в дом, потом домой", "Олег", List.of("дом"), true) == null,
                "The carry kept only its first leg: " + CompanionLocalCommands.route("отнеси ресы в дом, потом домой", "Олег", List.of("дом"), true));
        h.assertTrue(CompanionLocalCommands.route("охраняй дачу, потом домой", "Олег", markers, true) == null,
                "The guard kept only its first leg: " + CompanionLocalCommands.route("охраняй дачу, потом домой", "Олег", markers, true));
        // The names that hold «и» themselves are one name: nothing is left after them, so they stay local.
        h.assertTrue("companion place go сад и огород".equals(CompanionLocalCommands.route("иди к саду и огороду", "Олег", List.of("сад и огород"), true)),
                "«сад и огород»: " + CompanionLocalCommands.route("иди к саду и огороду", "Олег", List.of("сад и огород"), true));
        h.assertTrue("companion place go дом и баня".equals(CompanionLocalCommands.route("веди меня к дому и бане", "Олег", List.of("сад и огород", "дом и баня"), true)),
                "«дом и баня»: " + CompanionLocalCommands.route("веди меня к дому и бане", "Олег", List.of("сад и огород", "дом и баня"), true));
        // One leg stays local, and so does one leg with only the words that soften it.
        h.assertTrue("companion place go дача".equals(CompanionLocalCommands.route("иди к даче", "Олег", markers, true)),
                "«иди к даче»: " + CompanionLocalCommands.route("иди к даче", "Олег", markers, true));
        h.assertTrue("companion place go дача".equals(CompanionLocalCommands.route("иди к даче и всё", "Олег", markers, true)),
                "«иди к даче и всё»: " + CompanionLocalCommands.route("иди к даче и всё", "Олег", markers, true));
        h.assertTrue("companion place go дача".equals(CompanionLocalCommands.route("иди к даче, пожалуйста", "Олег", markers, true)),
                "«иди к даче, пожалуйста»: " + CompanionLocalCommands.route("иди к даче, пожалуйста", "Олег", markers, true));
        h.assertTrue("companion place guard дача".equals(CompanionLocalCommands.route("охраняй дачу и всё", "Олег", markers, true)),
                "«охраняй дачу и всё»: " + CompanionLocalCommands.route("охраняй дачу и всё", "Олег", markers, true));
        h.assertTrue("companion place carry дом".equals(CompanionLocalCommands.route("отнеси ресы в дом и всё", "Олег", List.of("дом"), true)),
                "«отнеси ресы в дом и всё»: " + CompanionLocalCommands.route("отнеси ресы в дом и всё", "Олег", List.of("дом"), true));
        h.succeed();
    }

    // ---- «забудь дачу»: the player's own list, one honest line, and no «какую?» for two named markers ----

    /** «забудь точку дачу», «забудь метку дачу», «забудь дачу», «удали точку дачу», «удали метку дачу»: the marker goes. */
    @GameTest(template = "empty")
    public static void everyForgetPhraseTakesTheMarkerItNamesOffTheList(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        for (String phrase : List.of("забудь точку дачу", "забудь метку дачу", "забудь дачу", "удали точку дачу", "удали метку дачу")) {
            store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition(), 1);
            var speech = CompanionPlaceSpeech.parse(phrase, "Олег", List.of("дача"), true);
            h.assertTrue(speech.kind() == CompanionPlaceSpeech.Kind.FORGET && "дача".equals(speech.name()),
                    "«" + phrase + "»: " + speech.kind() + " «" + speech.name() + "»");
            h.assertTrue(speech.question() == null, "«" + phrase + "» asked instead of forgetting: " + speech.question());
            h.assertTrue("companion place forget дача".equals(CompanionLocalCommands.route(phrase, "Олег", List.of("дача"), true)),
                    "«" + phrase + "» routed: " + CompanionLocalCommands.route(phrase, "Олег", List.of("дача"), true));
            var forgotten = CompanionMarkerBoard.forgetSpoken(owner, speech.name());
            h.assertTrue(forgotten.count() == 1 && forgotten.message().contains("«дача»"), "«" + phrase + "» answer: " + forgotten.message());
            h.assertTrue(forgotten.message().chars().filter(ch -> ch == '?').count() == 0, "A question instead of the line: " + forgotten.message());
            h.assertTrue(store.all(c.getOwnerId()).isEmpty(), "«" + phrase + "» left the marker: " + store.all(c.getOwnerId()));
        }
        h.succeed();
    }

    /** A name no marker has: one question with the known names, and the marker stays where it was. */
    @GameTest(template = "empty")
    public static void anUnknownForgetNameAsksOnceAndForgetsNothing(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition(), 1);
        var speech = CompanionPlaceSpeech.parse("забудь озеро", "Олег", List.of("дача"), true);
        h.assertTrue(speech.kind() == CompanionPlaceSpeech.Kind.UNKNOWN, "Unknown forget name: " + speech.kind());
        h.assertTrue(speech.question() != null && speech.question().contains("«дача»"), "The known names are not listed: " + speech.question());
        h.assertTrue(speech.question().chars().filter(ch -> ch == '?').count() == 1, "Not one question: " + speech.question());
        h.assertTrue(CompanionLocalCommands.route("забудь озеро", "Олег", List.of("дача"), true) == null, "An unknown name became a command");
        h.assertTrue(store.all(c.getOwnerId()).size() == 1, "An unknown name forgot the marker: " + store.all(c.getOwnerId()));
        // «Забудь остров» with the two islands he saved: no name fits, and the one question lists them both.
        var islands = CompanionPlaceSpeech.parse("забудь остров", "Олег", List.of("второй остров", "островок"), true);
        h.assertTrue(islands.question() != null && islands.question().contains("«второй остров»") && islands.question().contains("«островок»"),
                "The islands are not listed: " + islands.question());
        h.assertTrue(islands.question().chars().filter(ch -> ch == '?').count() == 1, "Not one question: " + islands.question());
        h.succeed();
    }

    /** «Забудь колодец» with «колодец» and «колодцы» saved: one question «какую?», and both stay. */
    @GameTest(template = "empty")
    public static void severalSimilarMarkersAskOnceAndBothStay(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        store.add(c.getOwnerId(), "колодец", h.getLevel().dimension(), c.blockPosition(), 1);
        store.add(c.getOwnerId(), "колодцы", h.getLevel().dimension(), c.blockPosition().offset(2, 0, 0), 2);
        var speech = CompanionPlaceSpeech.parse("забудь колодец", "Олег", List.of("колодец", "колодцы"), true);
        h.assertTrue(speech.kind() == CompanionPlaceSpeech.Kind.AMBIGUOUS, "Similar markers: " + speech.kind());
        h.assertTrue(speech.question().contains("«колодец»") && speech.question().contains("«колодцы»"), "Both not named: " + speech.question());
        h.assertTrue(speech.question().chars().filter(ch -> ch == '?').count() == 1, "Not one question: " + speech.question());
        h.assertTrue(CompanionLocalCommands.route("забудь колодец", "Олег", List.of("колодец", "колодцы"), true) == null,
                "An ambiguous name became a command");
        h.assertTrue(store.all(c.getOwnerId()).size() == 2, "An ambiguous name forgot a marker: " + store.all(c.getOwnerId()));
        h.succeed();
    }

    /** «Забудь всё» / «забудь все метки»: every marker goes, and the line names how many there were. */
    @GameTest(template = "empty")
    public static void forgettingEverythingNamesTheCountAndEmptiesTheList(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        for (int index = 1; index <= 3; index++)
            store.add(c.getOwnerId(), "метка" + index, h.getLevel().dimension(), c.blockPosition().offset(index, 0, 0), index);
        List<String> names = List.of("метка1", "метка2", "метка3");
        h.assertTrue(CompanionPlaceSpeech.parse("забудь всё", "Олег", names, true).kind() == CompanionPlaceSpeech.Kind.FORGET_ALL,
                "«забудь всё» is not the whole list");
        h.assertTrue("companion place forget".equals(CompanionLocalCommands.route("забудь все метки", "Олег", names, true)),
                "«забудь все метки» routed: " + CompanionLocalCommands.route("забудь все метки", "Олег", names, true));
        var forgotten = CompanionMarkerBoard.forgetAll(owner);
        h.assertTrue(forgotten.count() == 3 && forgotten.message().contains("3"), "No count: " + forgotten.message());
        h.assertTrue(forgotten.message().chars().filter(ch -> ch == '?').count() == 0, "A question instead of the line: " + forgotten.message());
        h.assertTrue(store.all(c.getOwnerId()).isEmpty(), "The list is not empty: " + store.all(c.getOwnerId()));
        var again = CompanionMarkerBoard.forgetAll(owner);
        h.assertTrue(again.count() == 0 && again.message().contains("нет"), "An empty list: " + again.message());
        h.succeed();
    }

    /** «Забудь дом»: the marker of that name goes; with no such marker it is the home he knows, and the line says so. */
    @GameTest(template = "empty")
    public static void theHomeWordIsForgottenOnlyWhenAMarkerHasThatName(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition(), 1);
        var home = CompanionPlaceSpeech.parse("забудь дом", "Олег", List.of("дача"), true);
        h.assertTrue(home.kind() == CompanionPlaceSpeech.Kind.HOME, "«забудь дом» without a marker: " + home.kind());
        h.assertTrue(home.question() != null && home.question().contains("дом") && home.question().chars().filter(ch -> ch == '?').count() == 0,
                "The home is not said as it is: " + home.question());
        h.assertTrue(CompanionLocalCommands.route("забудь дом", "Олег", List.of("дача"), true) == null, "The home word became a command");
        h.assertTrue(store.all(c.getOwnerId()).size() == 1, "The home word forgot a marker: " + store.all(c.getOwnerId()));
        // A marker named «дом» is a marker: that word takes it off the list, and the neighbour stays.
        store.add(c.getOwnerId(), "дом", h.getLevel().dimension(), c.blockPosition().offset(2, 0, 0), 2);
        var marker = CompanionPlaceSpeech.parse("забудь дом", "Олег", List.of("дача", "дом"), true);
        h.assertTrue(marker.kind() == CompanionPlaceSpeech.Kind.FORGET && "дом".equals(marker.name()),
                "A marker «дом»: " + marker.kind() + " «" + marker.name() + "»");
        var forgotten = CompanionMarkerBoard.forgetSpoken(owner, marker.name());
        h.assertTrue(forgotten.count() == 1 && forgotten.message().contains("«дом»"), "The marker «дом» was not forgotten: " + forgotten.message());
        var left = store.all(c.getOwnerId());
        h.assertTrue(left.size() == 1 && left.getFirst().name().equals("дача"), "The home word forgot more: " + left);
        h.succeed();
    }

    /** With «Метки на карте» off the list is still the player's: «забудь дачу» takes the marker off it. */
    @GameTest(template = "empty")
    public static void forgettingWorksWithTheFeatureSwitchedOff(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition(), 1);
        c.setMarkersEnabled(false);
        var speech = CompanionPlaceSpeech.parse("забудь дачу", "Олег", List.of("дача"), false);
        h.assertTrue(speech.kind() == CompanionPlaceSpeech.Kind.FORGET, "A switched-off feature lost the forget word: " + speech.kind());
        h.assertTrue("companion place forget дача".equals(CompanionLocalCommands.route("забудь дачу", "Олег", List.of("дача"), false)),
                "A switched-off feature routed: " + CompanionLocalCommands.route("забудь дачу", "Олег", List.of("дача"), false));
        h.assertTrue(CompanionMarkerBoard.forgetSpoken(owner, speech.name()).count() == 1, "The marker stayed with the switch off");
        h.assertTrue(store.all(c.getOwnerId()).isEmpty(), "The list is not empty: " + store.all(c.getOwnerId()));
        h.succeed();
    }

    /** Forgetting one marker leaves its neighbours, and a name that fits none changes nothing. */
    @GameTest(template = "empty")
    public static void forgettingOneMarkerLeavesItsNeighbours(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition(), 1);
        store.add(c.getOwnerId(), "колодец", h.getLevel().dimension(), c.blockPosition().offset(2, 0, 0), 2);
        store.add(c.getOwnerId(), "баня", h.getLevel().dimension(), c.blockPosition().offset(4, 0, 0), 3);
        var forgotten = CompanionMarkerBoard.forgetSpoken(owner, "даче");
        h.assertTrue(forgotten.count() == 1 && forgotten.message().contains("«дача»"), "The answer: " + forgotten.message());
        var left = store.all(c.getOwnerId());
        h.assertTrue(left.size() == 2 && left.stream().noneMatch(marker -> marker.name().equals("дача")), "The neighbours: " + left);
        h.assertTrue(CompanionMarkerBoard.forgetSpoken(owner, "озеру").count() == 0, "A name that fits none forgot something");
        h.assertTrue(store.all(c.getOwnerId()).size() == 2, "A name that fits none changed the list: " + store.all(c.getOwnerId()));
        h.succeed();
    }

    /** «Забудь дачу и колодец»: two names, two markers, both go — no «какую?». */
    @GameTest(template = "empty")
    public static void twoNamesInOneForgetPhraseAreBothForgotten(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition(), 1);
        store.add(c.getOwnerId(), "колодец", h.getLevel().dimension(), c.blockPosition().offset(2, 0, 0), 2);
        var speech = CompanionPlaceSpeech.parse("забудь дачу и колодец", "Олег", List.of("дача", "колодец"), true);
        h.assertTrue(speech.kind() == CompanionPlaceSpeech.Kind.FORGET && speech.question() == null,
                "Two names became a question: " + speech.kind() + " " + speech.question());
        h.assertTrue("companion place forget дачу и колодец".equals(CompanionLocalCommands.route("забудь дачу и колодец", "Олег", List.of("дача", "колодец"), true)),
                "Two names routed: " + CompanionLocalCommands.route("забудь дачу и колодец", "Олег", List.of("дача", "колодец"), true));
        var forgotten = CompanionMarkerBoard.forgetSpoken(owner, speech.name());
        h.assertTrue(forgotten.count() == 2 && forgotten.message().contains("«дача»") && forgotten.message().contains("«колодец»"),
                "Both not named: " + forgotten.message());
        h.assertTrue(store.all(c.getOwnerId()).isEmpty(), "A marker stayed: " + store.all(c.getOwnerId()));
        h.succeed();
    }

    /** Two markers named as two legs are no ambiguity: the model plans the whole route, not one walk. */
    @GameTest(template = "empty")
    public static void twoNamedLegsGoToTheModelInsteadOfAQuestion(GameTestHelper h) {
        List<String> islands = List.of("второй остров", "первый остров");
        String both = "иди на второй остров потом на первый остров, так 5 раз";
        var speech = CompanionPlaceSpeech.parse(both, "Олег", islands, true);
        h.assertTrue(speech.kind() == CompanionPlaceSpeech.Kind.NONE && speech.question() == null,
                "Two islands became a question: " + speech.kind() + " " + speech.question());
        h.assertTrue(CompanionLocalCommands.route(both, "Олег", islands, true) == null, "Two islands became one walk");
        List<String> two = List.of("второй остров", "островок");
        String back = "иди на второй остров потом на островок и обратно, и так 5 раз";
        var islandAndIslet = CompanionPlaceSpeech.parse(back, "Олег", two, true);
        h.assertTrue(islandAndIslet.kind() == CompanionPlaceSpeech.Kind.NONE && islandAndIslet.question() == null,
                "The island and the islet became a question: " + islandAndIslet.kind() + " " + islandAndIslet.question());
        h.assertTrue(CompanionLocalCommands.route(back, "Олег", two, true) == null, "The island and the islet became one walk");
        // One place that several markers fit still asks once.
        var one = CompanionPlaceSpeech.parse("иди к колодцу", "Олег", List.of("колодец", "колодцы"), true);
        h.assertTrue(one.kind() == CompanionPlaceSpeech.Kind.AMBIGUOUS, "One place with two markers: " + one.kind());
        h.assertTrue(CompanionLocalCommands.route("иди к колодцу", "Олег", List.of("колодец", "колодцы"), true) == null, "An ambiguous place walked");
        // «Иди к острову» with the two islands saved is still one question and no walk.
        var island = CompanionPlaceSpeech.parse("иди к острову", "Олег", two, true);
        h.assertTrue(island.question() != null && CompanionLocalCommands.route("иди к острову", "Олег", two, true) == null,
                "The island word walked without asking: " + island.kind());
        // A single name with a return trip stays local.
        h.assertTrue("companion place go дача".equals(CompanionLocalCommands.route("иди к даче и обратно", "Олег", List.of("дача"), true)),
                "«иди к даче и обратно»: " + CompanionLocalCommands.route("иди к даче и обратно", "Олег", List.of("дача"), true));
        h.succeed();
    }

    /** «Иди ко мне» and «веди меня ко мне»: a person word is never a marker, even when a marker is called «мне». */
    @GameTest(template = "empty")
    public static void personWordsAreNeverMarkerNames(GameTestHelper h) {
        List<String> markers = List.of("мне", "дача");
        for (String phrase : List.of("иди ко мне", "иди к нам", "подойди ко мне", "веди меня ко мне")) {
            var speech = CompanionPlaceSpeech.parse(phrase, "Олег", markers, true);
            h.assertTrue(speech.kind() == CompanionPlaceSpeech.Kind.NONE && speech.question() == null,
                    "«" + phrase + "»: " + speech.kind() + " " + speech.question());
        }
        h.assertTrue("companion summon".equals(CompanionLocalCommands.route("иди ко мне", "Олег", markers, true)),
                "«иди ко мне» lost the summon: " + CompanionLocalCommands.route("иди ко мне", "Олег", markers, true));
        h.assertTrue("companion place go дом".equals(CompanionLocalCommands.route("иди к дому", "Олег", List.of("дом"), true)),
                "«иди к дому»: " + CompanionLocalCommands.route("иди к дому", "Олег", List.of("дом"), true));
        h.assertTrue(CompanionPlaceSpeech.parse("забудь меня", "Олег", markers, true).kind() == CompanionPlaceSpeech.Kind.NONE,
                "«забудь меня» asked about a marker");
        h.succeed();
    }

    /** «Забудь цель» and «забудь задачу» stay the orders they were: the goal and stop grammar own those words. */
    @GameTest(template = "empty")
    public static void forgettingAnOrderIsNotAMarkerQuestion(GameTestHelper h) {
        List<String> markers = List.of("дача");
        h.assertTrue("companion goal cancel".equals(CompanionLocalCommands.route("забудь цель", "Олег", markers, true)),
                "«забудь цель»: " + CompanionLocalCommands.route("забудь цель", "Олег", markers, true));
        h.assertTrue(CompanionPlaceSpeech.parse("забудь цель", "Олег", markers, true).kind() == CompanionPlaceSpeech.Kind.NONE,
                "«забудь цель» asked about a marker");
        h.assertTrue(CompanionLocalCommands.route("забудь задачу", "Олег", markers, true) == null,
                "«забудь задачу» became a marker question: " + CompanionLocalCommands.route("забудь задачу", "Олег", markers, true));
        h.assertTrue(CompanionLocalCommands.route("забудь про молоко", "Олег", markers, true) == null,
                "«забудь про молоко» became a marker question: " + CompanionLocalCommands.route("забудь про молоко", "Олег", markers, true));
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

    /** An owner online in the Nether while he stands in the Overworld: his feet are saved, and the line says so. */
    @GameTest(template = "empty")
    public static void anOwnerOnlineInAnotherDimensionIsSaidHonestly(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        owner.teleportTo(nether, 1000.5, 70, 1000.5, 0, 0);
        h.assertTrue(owner.level() == nether, "The owner is not in the Nether: " + owner.level().dimension().location());
        h.assertTrue(!owner.blockPosition().equals(c.blockPosition()), "The two dimensions share a point");
        var result = c.rememberPlace("портал");
        h.assertTrue(result.success(), "The marker was refused: " + result.message());
        h.assertTrue(result.message().contains("стою я"), "The owner's point in another dimension was taken as his: " + result.message());
        h.assertTrue(result.message().contains("в world") && !result.message().contains("в nether"),
                "The marker was placed in the owner's dimension: " + result.message());
        var markers = CompanionMarkers.get(h.getLevel().getServer()).all(c.getOwnerId());
        h.assertTrue(markers.size() == 1, "Not saved: " + markers);
        var marker = markers.getFirst();
        h.assertTrue(marker.pos().equals(c.blockPosition()), "Not his own feet: " + marker.pos() + " he at " + c.blockPosition());
        h.assertTrue(marker.dimension().equals(h.getLevel().dimension()), "The marker took the owner's dimension: " + marker.dimension());
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

    /** The screen's answer names the cause: a vanished marker is not a bad name, and each bad name has its own line. */
    @GameTest(template = "empty")
    public static void theSaveTellsAVanishedMarkerFromANameThatDidNotFit(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition(), 1);
        var second = store.add(c.getOwnerId(), "колодец", h.getLevel().dimension(), c.blockPosition().offset(2, 0, 0), 2).marker();
        BlockPos wanted = c.blockPosition().offset(4, 0, 2);
        var gone = CompanionMarkerBoard.save(owner, UUID.randomUUID(), "новое имя", wanted.getX(), wanted.getY(), wanted.getZ());
        h.assertTrue(!gone.renamed() && !gone.moved(), "A vanished marker reported a save: " + gone);
        h.assertTrue(gone.message().contains("уже нет") && !gone.message().contains("символов"), "A vanished marker blamed the name: " + gone.message());
        var absent = CompanionMarkerBoard.save(null, second.id(), "новое имя", wanted.getX(), wanted.getY(), wanted.getZ());
        h.assertTrue(!absent.renamed() && absent.message().contains("нет на сервере"), "An absent player was not said: " + absent.message());
        var tooLong = CompanionMarkerBoard.save(owner, second.id(), "а".repeat(CompanionMarkerNames.MAX + 1), wanted.getX(), wanted.getY(), wanted.getZ());
        h.assertTrue(tooLong.message().contains("до " + CompanionMarkerNames.MAX + " символов"), "No length line: " + tooLong.message());
        var coordinates = CompanionMarkerBoard.save(owner, second.id(), "100, 64, 200", wanted.getX(), wanted.getY(), wanted.getZ());
        h.assertTrue(coordinates.message().contains("координаты"), "No coordinates line: " + coordinates.message());
        var taken = CompanionMarkerBoard.save(owner, second.id(), "дача", wanted.getX(), wanted.getY(), wanted.getZ());
        h.assertTrue(taken.message().contains("уже есть у метки «дача»"), "No duplicate line: " + taken.message());
        h.assertTrue(!tooLong.message().equals(coordinates.message()) && !coordinates.message().equals(taken.message()),
                "One line for every cause: " + tooLong.message() + " / " + coordinates.message() + " / " + taken.message());
        // No refused save moved the marker; the name that fits saves it and moves the point.
        var untouched = store.byId(c.getOwnerId(), second.id());
        h.assertTrue(untouched.name().equals("колодец") && untouched.pos().equals(second.pos()), "A refused save moved the marker: " + untouched);
        var saved = CompanionMarkerBoard.save(owner, second.id(), "колодец2", wanted.getX(), wanted.getY(), wanted.getZ());
        h.assertTrue(saved.renamed() && saved.moved() && "Метка сохранена.".equals(saved.message()), "The save: " + saved);
        var landed = store.byId(c.getOwnerId(), second.id());
        h.assertTrue(landed.name().equals("колодец2") && landed.pos().equals(wanted), "The accepted save did not land: " + landed);
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
        h.assertTrue(added.place() != null && added.place().name().equals("лагерь"), "Not added: " + added);
        h.assertTrue(added.place().x() == at.getX() && added.place().y() == at.getY() && added.place().z() == at.getZ(), "Not at the player: " + added);
        h.assertTrue("world".equals(added.place().world()), "Wrong world name: " + added.place().world());
        h.assertTrue(added.message().contains("Запомнил место «лагерь»"), "No honest line: " + added.message());
        var empty = CompanionMarkerBoard.addHere(owner, "");
        h.assertTrue(empty.place() == null && empty.message().contains("пустым"), "An empty name was added: " + empty);
        h.succeed();
    }

    /** A name the store refuses in the screen's add is said as that refusal, never as a missing player. */
    @GameTest(template = "empty")
    public static void theAddSaysItsOwnRefusalInsteadOfAMissingPlayer(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var coordinates = CompanionMarkerBoard.addHere(owner, "100, 64, 200");
        h.assertTrue(coordinates.place() == null && coordinates.message().contains("координаты"), "A coordinate name was added: " + coordinates);
        h.assertTrue(!coordinates.message().contains("нет на сервере"), "The refusal blamed the player: " + coordinates.message());
        var tooLong = CompanionMarkerBoard.addHere(owner, "а".repeat(CompanionMarkerNames.MAX + 1));
        h.assertTrue(tooLong.place() == null && tooLong.message().contains("до " + CompanionMarkerNames.MAX + " символов"),
                "No length line: " + tooLong.message());
        h.assertTrue(!tooLong.message().contains("нет на сервере"), "The length refusal blamed the player: " + tooLong.message());
        // A vanished player is still said as the missing player, and nothing else.
        var absent = CompanionMarkerBoard.addHere(null, "дача");
        h.assertTrue(absent.place() == null && absent.message().contains("нет на сервере"), "A missing player was not said: " + absent);
        // And the add that fits still lands where the player stands.
        var added = CompanionMarkerBoard.addHere(owner, "лагерь");
        h.assertTrue(added.place() != null && added.message().contains("Запомнил место «лагерь»"), "The add: " + added);
        h.succeed();
    }

    /** The chat's recognition copy follows every settings change at once, and pull reads the list on the server thread. */
    @GameTest(template = "empty")
    public static void theRecognitionCopyFollowsTheServerList(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        var added = CompanionMarkerBoard.addHere(owner, "дача");
        h.assertTrue(added.place() != null, "The marker was refused");
        h.assertTrue(CompanionMarkerView.names().contains("дача"), "The new name did not reach recognition: " + CompanionMarkerView.names());
        var marker = store.all(c.getOwnerId()).getFirst();
        h.assertTrue(CompanionMarkerBoard.rename(owner, marker.id(), "дача2"), "The rename was refused");
        h.assertTrue(CompanionMarkerView.names().contains("дача2") && !CompanionMarkerView.names().contains("дача"),
                "The rename did not reach recognition: " + CompanionMarkerView.names());
        h.assertTrue(CompanionMarkerBoard.forget(owner, marker.id()), "The delete was refused");
        h.assertTrue(!CompanionMarkerView.names().contains("дача2"), "The delete did not reach recognition: " + CompanionMarkerView.names());
        h.assertTrue(CompanionMarkerBoard.addHere(owner, "колодец").place() != null, "The second marker was refused");
        boolean[] pulled = {false};
        CompanionMarkerView.pull(owner, true, (names, on) -> pulled[0] = names.contains("колодец") && on);
        h.succeedWhen(() -> h.assertTrue(pulled[0], "pull did not deliver the names on the server thread: " + CompanionMarkerView.names()));
    }

    /** The recognition copy is one snapshot: a phrase is routed against names and switch from the same publication. */
    @GameTest(template = "empty")
    public static void theRecognitionSnapshotNeverPairsNewNamesWithTheOldSwitch(GameTestHelper h) {
        CompanionMarkerView.deliver(List.of("дача"), false);
        var off = CompanionMarkerView.snapshot();
        h.assertTrue(off.names().equals(List.of("дача")) && !off.enabled(), "The switched-off snapshot: " + off);
        h.assertTrue(CompanionLocalCommands.route("иди к даче", "Олег") == null, "The router walked with the switch off");
        CompanionMarkerView.deliver(List.of("колодец"), true);
        var on = CompanionMarkerView.snapshot();
        h.assertTrue(on.names().equals(List.of("колодец")) && on.enabled(), "The switched-on snapshot: " + on);
        h.assertTrue("companion place go колодец".equals(CompanionLocalCommands.route("иди к колодцу", "Олег")),
                "The router missed the new snapshot: " + CompanionLocalCommands.route("иди к колодцу", "Олег"));
        // Two publications alternate; every read must be one whole pair, never a name with the other switch.
        CompanionMarkerView.deliver(List.of("дача"), true);
        var torn = new java.util.concurrent.atomic.AtomicReference<String>();
        var done = new java.util.concurrent.CountDownLatch(1);
        Thread writer = new Thread(() -> {
            try {
                for (int index = 0; index < 200_000; index++) {
                    CompanionMarkerView.deliver(List.of("дача"), true);
                    CompanionMarkerView.deliver(List.of("колодец"), false);
                }
            } finally {
                done.countDown();
            }
        }, "marker-view-writer");
        writer.start();
        int reads = 0;
        do {
            var snapshot = CompanionMarkerView.snapshot();
            boolean first = snapshot.names().equals(List.of("дача")) && snapshot.enabled();
            boolean second = snapshot.names().equals(List.of("колодец")) && !snapshot.enabled();
            if (!first && !second) { torn.set(snapshot.toString()); break; }
            reads++;
        } while (done.getCount() > 0 && reads < 2_000_000);
        try {
            writer.join(5000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        h.assertTrue(torn.get() == null, "A torn read paired the names with the other switch: " + torn.get());
        h.assertTrue(reads > 0, "No read of the copy happened");
        // Leave the shared copy as the rest of the suite expects it.
        CompanionMarkerView.deliver(List.of(), true);
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

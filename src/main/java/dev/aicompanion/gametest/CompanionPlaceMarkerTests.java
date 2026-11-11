package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionMarkerView;
import dev.aicompanion.client.CompanionParkHold;
import dev.aicompanion.client.CompanionPlaceSpeech;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.CompanionSettingsLayout;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionCrafting;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionMarkerBoard;
import dev.aicompanion.entity.CompanionMarkerNames;
import dev.aicompanion.entity.CompanionMarkers;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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

    /**
     * A forget word about a thing, not about the list: «забудь, что я говорил», «забудь, где дача», «забудь всё про
     * молоко», «забудь всё про метку», «забудь про дачу» name no marker and stay the model's. «Забудь всё» and
     * «забудь все метки» still empty the list.
     */
    @GameTest(template = "empty")
    public static void aForgetWordAboutAThingLeavesTheListAlone(GameTestHelper h) {
        List<String> markers = List.of("дача", "колодец");
        for (String phrase : List.of("забудь, что я говорил", "забудь как мы ходили за грибами", "забудь когда это было",
                "забудь где дача", "забудь всё про молоко", "забудь всё про метку", "забудь про дачу", "забудь об этом")) {
            var speech = CompanionPlaceSpeech.parse(phrase, "Олег", markers, true);
            h.assertTrue(speech.kind() == CompanionPlaceSpeech.Kind.NONE && speech.question() == null,
                    "«" + phrase + "» became a marker word: " + speech.kind() + " " + speech.question());
            h.assertTrue(CompanionLocalCommands.route(phrase, "Олег", markers, true) == null,
                    "«" + phrase + "» routed: " + CompanionLocalCommands.route(phrase, "Олег", markers, true));
        }
        // The whole list still goes: «забудь всё» with nothing after it, and «забудь все метки».
        h.assertTrue(CompanionPlaceSpeech.parse("забудь всё", "Олег", markers, true).kind() == CompanionPlaceSpeech.Kind.FORGET_ALL,
                "«забудь всё» is not the whole list");
        h.assertTrue(CompanionPlaceSpeech.parse("забудь все метки", "Олег", markers, true).kind() == CompanionPlaceSpeech.Kind.FORGET_ALL,
                "«забудь все метки» is not the whole list");
        // The list itself is untouched by the phrases that stayed the model's, and «забудь всё» empties it.
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition(), 1);
        store.add(c.getOwnerId(), "колодец", h.getLevel().dimension(), c.blockPosition().offset(2, 0, 0), 2);
        h.assertTrue(CompanionPlaceSpeech.parse("забудь всё про молоко", "Олег", List.of("дача", "колодец"), true)
                        .kind() != CompanionPlaceSpeech.Kind.FORGET_ALL, "«забудь всё про молоко» emptied the list");
        h.assertTrue(store.all(c.getOwnerId()).size() == 2, "«забудь всё про молоко» changed the list: " + store.all(c.getOwnerId()));
        var forgotten = CompanionMarkerBoard.forgetAll(owner);
        h.assertTrue(forgotten.count() == 2 && store.all(c.getOwnerId()).isEmpty(),
                "«забудь всё» left markers: " + forgotten.count() + " " + store.all(c.getOwnerId()));
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

    /**
     * «Патрулируй от дома до дачи 100 раз»: the count is a plan-level repeat beside the written steps, and only a walking
     * route may carry it. The written steps stay capped at 12, so two steps and repeat 50 are one legal plan.
     */
    @GameTest(template = "empty")
    public static void theCodecTakesARepeatOnlyForAWalkingRoute(GameTestHelper h) {
        String route = "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"}],\"repeat\":50}";
        var reply = CompanionPlanCodec.decode(toolCall(route));
        h.assertTrue(reply.actions().size() == 2 && reply.repeat() == 50, "The repeat was lost: " + reply);
        h.assertTrue(reply.text().contains("50") && reply.text().contains("«дом»") && reply.text().contains("«дача»"),
                "The player does not hear the count and the route: " + reply.text());
        // Without the field the plan runs once, as it did before.
        var once = CompanionPlanCodec.decode(toolCall("{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дача\"}]}"));
        h.assertTrue(once.repeat() == 1 && once.actions().size() == 1, "A plan without repeat changed: " + once);
        // repeat 1 beside another action is the same as no repeat at all.
        var mixed = CompanionPlanCodec.decode(toolCall("{\"steps\":[{\"action\":\"store_items\",\"item\":\"resources\"}],\"repeat\":1}"));
        h.assertTrue(mixed.repeat() == 1 && "store_items".equals(mixed.actions().getFirst().type()), "repeat 1 refused another action: " + mixed);
        // Any other action in the plan, a count out of range or a fractional one spoils it whole.
        String walk = "{\"action\":\"go_to_place\",\"item\":\"дом\"}";
        for (String bad : List.of(
                "{\"steps\":[" + walk + ",{\"action\":\"store_items\",\"item\":\"resources\"}],\"repeat\":5}",
                "{\"steps\":[" + walk + "],\"repeat\":0}",
                "{\"steps\":[" + walk + "],\"repeat\":101}",
                "{\"steps\":[" + walk + "],\"repeat\":2.5}")) {
            boolean refused = false;
            try { CompanionPlanCodec.decode(toolCall(bad)); } catch (RuntimeException expected) { refused = true; }
            h.assertTrue(refused, "Accepted: " + bad);
        }
        // The 12-step cap is on the written steps, not on the walks the repeat adds; 13 steps with a repeat are refused.
        StringBuilder many = new StringBuilder("{\"steps\":[");
        for (int step = 0; step < CompanionPlanCodec.MAX_STEPS + 1; step++) many.append(step == 0 ? "" : ",").append(walk);
        many.append("],\"repeat\":2}");
        boolean refused = false;
        try { CompanionPlanCodec.decode(toolCall(many.toString())); } catch (RuntimeException expected) { refused = true; }
        h.assertTrue(refused, "13 written steps with a repeat were accepted: " + many);
        // The fallback text carries the repeat beside the steps just as well.
        JsonObject fallbackText = new JsonObject();
        fallbackText.addProperty("content", "<|DSML|invoke name=\"queue_companion_plan\">"
                + "<|DSML|parameter name=\"steps\" string=\"false\">[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"}]</|DSML|parameter>"
                + "<|DSML|parameter name=\"repeat\" string=\"false\">5</|DSML|parameter></|DSML|invoke>");
        var fallback = CompanionPlanCodec.decode(fallbackText);
        h.assertTrue(fallback.repeat() == 5 && fallback.actions().size() == 2, "The fallback text lost the repeat: " + fallback);
        h.succeed();
    }

    /** The queue gets the route as many times as the repeat says, in order: 2 steps × 50 are 100 queued actions. */
    @GameTest(template = "empty")
    public static void theRepeatExpandsIntoTheQueuedStepsInOrder(GameTestHelper h) {
        var written = CompanionPlanCodec.parsePlan(JsonParser.parseString(
                "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"}],\"repeat\":50}"));
        var queued = CompanionPlanCodec.repeated(written, 50);
        h.assertTrue(queued.size() == 100, "The route was not repeated: " + queued.size());
        h.assertTrue(queued.get(0).item().equals("дом") && queued.get(1).item().equals("дача")
                && queued.get(2).item().equals("дом") && queued.get(99).item().equals("дача"),
                "The legs are out of order: " + queued.subList(0, 4) + " … " + queued.getLast());
        h.assertTrue(queued.stream().allMatch(step -> "go_to_place".equals(step.type())), "A step of another kind was queued");
        h.assertTrue(CompanionPlanCodec.repeated(written, 1).equals(written), "One repeat changed the plan");
        h.succeed();
    }

    /** «Патрулируй дом→дача→дом 2 раза»: the spoken route keeps every leg in order, and the count in the player's word. */
    @GameTest(template = "empty")
    public static void theSpokenRouteKeepsEveryLegAndItsNumeral(GameTestHelper h) {
        var two = CompanionPlanCodec.decode(toolCall(
                "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"},"
                        + "{\"action\":\"go_to_place\",\"item\":\"дом\"}],\"repeat\":2}"));
        h.assertTrue(two.text().contains("Маршрут «дом» → «дача» → «дом» — 2 раза."),
                "The return leg or the count is not said: " + two.text());
        var five = CompanionPlanCodec.decode(toolCall(
                "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"}],\"repeat\":5}"));
        h.assertTrue(five.text().contains("— 5 раз."), "The count is not said in the player's word: " + five.text());
        h.assertTrue(!five.text().contains("5 раза") && !two.text().contains("2 раз."), "A wrong numeral: " + two.text() + " / " + five.text());
        // «11 раз», «21 раз», «22 раза»: the teens and the ones decide the word, not the last digit alone.
        h.assertTrue("раз".equals(CompanionDialogue.times(11)) && "раз".equals(CompanionDialogue.times(21))
                && "раза".equals(CompanionDialogue.times(22)) && "раза".equals(CompanionDialogue.times(4)),
                "The numeral does not follow the count");
        h.succeed();
    }

    /**
     * «Поставь метки и патрулируй дом→дача 50 раз»: a placement wording that changes nothing returns the decoded plan as it
     * was, and one that adds a step still keeps the count for the walk.
     */
    @GameTest(template = "empty")
    public static void aRewrittenPlanKeepsTheRepeatItWasDecodedWith(GameTestHelper h) {
        var decoded = CompanionPlanCodec.decode(toolCall(
                "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"}],\"repeat\":50}"));
        var unchanged = CompanionPlanRequirements.apply("поставь метки и патрулируй дом и дачу 50 раз", decoded);
        h.assertTrue(unchanged.repeat() == 50 && unchanged.actions().equals(decoded.actions()),
                "The rewritten plan lost its repeat: " + unchanged);
        h.assertTrue(unchanged.text().contains("50"), "The spoken count disappeared: " + unchanged.text());
        var added = CompanionPlanRequirements.apply("поставь сундук и патрулируй дом и дачу 50 раз", decoded);
        h.assertTrue(added.repeat() == 50 && added.actions().stream().anyMatch(step -> "place_block".equals(step.type())),
                "The added chest dropped the count: " + added);
        h.assertTrue(added.text().contains("— 50 раз."), "The count is not said with the added step: " + added.text());
        h.succeed();
    }

    /** Two invokes in one answer: one and the same repeat is the plan's, two different counts spoil it. */
    @GameTest(template = "empty")
    public static void twoInvokesMayNotDisagreeOnTheRepeat(GameTestHelper h) {
        String route = "[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"}]";
        String same = invoke(route, "5") + invoke(route, "5");
        JsonObject acceptedText = new JsonObject();
        acceptedText.addProperty("content", same);
        var accepted = CompanionPlanCodec.decode(acceptedText);
        h.assertTrue(accepted.repeat() == 5 && accepted.actions().size() == 4, "Two agreeing invokes were refused: " + accepted);

        String different = invoke(route, "5") + invoke(route, "2");
        JsonObject refusedText = new JsonObject();
        refusedText.addProperty("content", different);
        boolean refused = false;
        try { CompanionPlanCodec.decode(refusedText); } catch (RuntimeException expected) { refused = true; }
        h.assertTrue(refused, "Two invokes with different counts were accepted: " + different);

        // A repeat parameter of another invoke is not the plan's.
        JsonObject otherText = new JsonObject();
        otherText.addProperty("content", "<|DSML|invoke name=\"queue_companion_plan\">"
                + "<|DSML|parameter name=\"steps\" string=\"false\">" + route + "</|DSML|parameter></|DSML|invoke>"
                + "<|DSML|invoke name=\"remember_note\">"
                + "<|DSML|parameter name=\"repeat\" string=\"false\">9</|DSML|parameter></|DSML|invoke>");
        var other = CompanionPlanCodec.decode(otherText);
        h.assertTrue(other.repeat() == 1 && other.actions().size() == 2, "Another invoke's repeat became the plan's: " + other);
        h.succeed();
    }

    /** «Запомни это место как дача и патрулируй дом→дача 50 раз»: remembering once, then the route fifty times. */
    @GameTest(template = "empty")
    public static void aRememberedPlaceBesideARepeatRunsOnce(GameTestHelper h) {
        var reply = CompanionPlanCodec.decode(toolCall(
                "{\"steps\":[{\"action\":\"remember_place\",\"item\":\"дача\"},{\"action\":\"go_to_place\",\"item\":\"дом\"},"
                        + "{\"action\":\"go_to_place\",\"item\":\"дача\"}],\"repeat\":50}"));
        h.assertTrue(reply.repeat() == 50 && reply.actions().size() == 3, "The remembered place spoiled the plan: " + reply);
        var queued = CompanionPlanCodec.repeated(reply.actions(), reply.repeat());
        h.assertTrue(queued.size() == 101, "The remembered place was repeated: " + queued.size());
        h.assertTrue("remember_place".equals(queued.getFirst().type()) && "дача".equals(queued.getFirst().item()),
                "Remembering is not first: " + queued.getFirst());
        h.assertTrue(queued.subList(1, queued.size()).stream().allMatch(step -> "go_to_place".equals(step.type())),
                "A non-walk step went into the route: " + queued.subList(0, 4));
        h.assertTrue("дом".equals(queued.get(1).item()) && "дача".equals(queued.get(2).item())
                && "дом".equals(queued.get(3).item()) && "дача".equals(queued.get(100).item()),
                "The route is out of order: " + queued.subList(0, 4) + " … " + queued.getLast());
        h.assertTrue(reply.text().contains("Запомнил это место как «дача»") && reply.text().contains("— 50 раз."),
                "The player does not hear the place and the count: " + reply.text());
        h.succeed();
    }

    /** A patrol's report names the same arrival every lap: the model history keeps each distinct line once, with its count. */
    @GameTest(template = "empty")
    public static void theHistoryCollapsesRepeatedLegLines(GameTestHelper h) {
        StringBuilder report = new StringBuilder();
        for (int lap = 0; lap < 50; lap++)
            report.append("Пришёл к метке «дом»: 14, 65, 8.\n").append("Пришёл к метке «дача»: 20, 65, 6.\n");
        String collapsed = OpenRouterClient.collapsedReport(report.toString());
        h.assertTrue(collapsed.lines().count() == 2, "Identical legs were not collapsed: " + collapsed);
        h.assertTrue(collapsed.contains("Пришёл к метке «дом»: 14, 65, 8. (×50)")
                && collapsed.contains("Пришёл к метке «дача»: 20, 65, 6. (×50)"), "The count of the legs is lost: " + collapsed);
        OpenRouterClient.resetSession();
        // A reply first: a history with a system prompt would read the config, which a gametest server has not loaded.
        OpenRouterClient.recordReply(OpenRouterClient.generation(), "Ладно, иду.");
        OpenRouterClient.recordActionResult(report.toString());
        String stored = OpenRouterClient.historySnapshot().getLast().get("content").getAsString();
        h.assertTrue(stored.length() < 200 && stored.contains("(×50)"), "The raw report reached the history: " + stored);
        OpenRouterClient.resetSession();
        h.succeed();
    }

    private static String invoke(String steps, String repeat) {
        return "<|DSML|invoke name=\"queue_companion_plan\">"
                + "<|DSML|parameter name=\"steps\" string=\"false\">" + steps + "</|DSML|parameter>"
                + "<|DSML|parameter name=\"repeat\" string=\"false\">" + repeat + "</|DSML|parameter></|DSML|invoke>";
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

    // ---- the patrol: one plan, several walks ----

    /**
     * «Патрулируй от дома до дачи и обратно 2 раза»: three written legs and repeat 2 become six real walks, and he visits
     * both markers — home four times, the dacha twice — by the normal path, one leg after the other.
     */
    @GameTest(template = "large_search", timeoutTicks = 1600)
    public static void aPatrolWithRepeatWalksEveryLegItWasGiven(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        BlockPos home = h.absolutePos(new BlockPos(14, 1, 8));
        BlockPos dacha = h.absolutePos(new BlockPos(20, 1, 6));
        store.add(c.getOwnerId(), "дом", h.getLevel().dimension(), home, 1);
        store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), dacha, 2);
        var written = CompanionPlanCodec.parsePlan(JsonParser.parseString(
                "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"},{\"action\":\"go_to_place\",\"item\":\"дом\"}],\"repeat\":2}"));
        h.assertTrue(written.size() == 3, "The codec expanded the written steps: " + written);
        var queued = CompanionPlanCodec.repeated(written, 2);
        h.assertTrue(queued.size() == 6, "The patrol was not expanded: " + queued.size());
        var plan = CompanionPlanRunner.run(queued,
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-marker-patrol", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The patrol is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            String report = outcome.report();
            h.assertTrue(arrivals(report) == 6, "The six legs were not all walked: " + report);
            h.assertTrue(arrivals(report, "дом") == 4 && arrivals(report, "дача") == 2, "The route was not the one written: " + report);
            h.assertTrue(outcome.skipped().isEmpty() && !report.contains("Не выполнено"), "A leg was skipped: " + report);
            h.assertTrue(report.lastIndexOf("Пришёл к метке «дом»") > report.lastIndexOf("Пришёл к метке «дача»"),
                    "The patrol did not end at home: " + report);
        });
    }

    /** «Стоп» mid-patrol: the leg in hand ends, the rest of the six are not walked, and nothing is listed as not done. */
    @GameTest(template = "large_search", timeoutTicks = 1600)
    public static void aStopMidPatrolCancelsTheRest(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        store.add(c.getOwnerId(), "дом", h.getLevel().dimension(), h.absolutePos(new BlockPos(14, 1, 8)), 1);
        store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), h.absolutePos(new BlockPos(20, 1, 6)), 2);
        var queued = CompanionPlanCodec.repeated(CompanionPlanCodec.parsePlan(JsonParser.parseString(
                "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"},{\"action\":\"go_to_place\",\"item\":\"дом\"}],\"repeat\":2}")), 2);
        java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        int[] legs = {0};
        boolean[] working = {false};
        h.onEachTick(() -> {
            boolean now = c.isWorking() || c.isGatheringTask();
            if (working[0] && !now) legs[0]++;
            working[0] = now;
            if (legs[0] >= 2) stop.set(true);
        });
        var plan = CompanionPlanRunner.run(queued,
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-marker-patrol-stop", stop::get));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The patrol is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            h.assertTrue(outcome.cancelled(), "The stop did not cancel the patrol: " + outcome.report());
            h.assertTrue(outcome.skipped().isEmpty(), "A cancelled patrol listed legs as not done: " + outcome.skipped());
            h.assertTrue(outcome.report().contains("План остановлен:"), "The stop is not said: " + outcome.report());
            h.assertTrue(arrivals(outcome.report()) < 6, "The patrol walked on after the stop: " + outcome.report());
            h.assertTrue(legs[0] >= 2, "The stop came before the patrol walked: " + legs[0]);
        });
    }

    /** A patrol whose first leg names no known marker: it fails there, the other five legs are not walked, nothing is retried. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void anUnknownMarkerOnTheFirstLegEndsThePatrolWithoutRepeating(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        CompanionMarkers.get(h.getLevel().getServer()).add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition().offset(6, 0, 0), 1);
        var queued = CompanionPlanCodec.repeated(CompanionPlanCodec.parsePlan(JsonParser.parseString(
                "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"озеро\"},{\"action\":\"go_to_place\",\"item\":\"дача\"}],\"repeat\":2}")), 2);
        h.assertTrue(queued.size() == 4, "The patrol was not expanded: " + queued.size());
        var plan = CompanionPlanRunner.run(queued,
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-marker-patrol-unknown", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The patrol is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            String report = outcome.report();
            h.assertTrue(report.contains("Не знаю метки «озеро»"), "The unknown name is not said: " + report);
            h.assertTrue(outcome.recoveries() == 0 && !report.contains("Повтор шага"), "The first leg was repeated: " + report);
            h.assertTrue(outcome.skipped().size() == 3, "The legs after the failed one are not listed: " + outcome.skipped());
            h.assertTrue(arrivals(report, "дача") == 0, "He walked to the dacha after the first leg failed: " + report);
            h.assertTrue(c.workPhase().isEmpty(), "A walk started for the unknown name: " + c.workPhase());
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

    // ---- the owner's five-step plan: home, take, mine, set down a chest, put the things into it ----

    /** 27 things that are neither logs, planks nor a chest, so a test's chest has to be made from the wood it gives him. */
    private static final Item[] FILLER = {Items.DIRT, Items.GRAVEL, Items.SAND, Items.FLINT, Items.ANDESITE, Items.DIORITE,
            Items.GRANITE, Items.TUFF, Items.CALCITE, Items.DEEPSLATE, Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
            Items.REDSTONE, Items.LAPIS_LAZULI, Items.COAL, Items.CHARCOAL, Items.TORCH, Items.STICK, Items.STRING,
            Items.FEATHER, Items.LEATHER, Items.BONE, Items.GUNPOWDER, Items.BRICK, Items.NETHER_BRICK, Items.AMETHYST_SHARD};

    /**
     * The plan the owner gave: «дом», take the things from the chest there, «шахта», set down a chest, put the things into
     * it. After the take one cell is left and there is no bench at the mine, so the chest is made there: the bench is made
     * and set down first, its cell comes free for the planks, and the taken things end up in the new chest.
     */
    @GameTest(template = "large_search", timeoutTicks = 1600)
    public static void theFiveStepPlanPlacesTheChestAndStoresTheTakenThingsWithANearlyFullBag(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        BlockPos homeChest = new BlockPos(4, 1, 6);
        h.setBlock(homeChest, Blocks.CHEST);
        ((Container) h.getBlockEntity(homeChest)).setItem(0, new ItemStack(Items.BRICK, 30));
        ((Container) h.getBlockEntity(homeChest)).setItem(1, new ItemStack(Items.NETHER_BRICK, 30));
        var store = CompanionMarkers.get(h.getLevel().getServer());
        store.add(c.getOwnerId(), "дом", h.getLevel().dimension(), h.absolutePos(homeChest), 1);
        BlockPos mine = new BlockPos(20, 1, 20);
        store.add(c.getOwnerId(), "шахта", h.getLevel().dimension(), h.absolutePos(mine), 1);
        owner.teleportTo(h.absolutePos(mine).getX() + .5, h.absolutePos(mine).getY(), h.absolutePos(mine).getZ() + .5);
        // 24 things and three logs take 25 cells: two are free, and the take fills both of them — the bag is full.
        for (int slot = 0; slot < 24; slot++) c.setItem(slot, new ItemStack(FILLER[slot], 2));
        c.setItem(24, new ItemStack(Items.OAK_LOG, 3));
        var plan = CompanionPlanRunner.run(List.of(
                        new PlannedAction("go_to_place", 0, 1, "дом"),
                        new PlannedAction("take_items", 0, 64, "all"),
                        new PlannedAction("go_to_place", 0, 1, "шахта"),
                        new PlannedAction("place_block", 0, 1, "minecraft:chest"),
                        new PlannedAction("store_items", 0, 1, "resources")),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-marker-five-steps", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            h.assertTrue(outcome.skipped().isEmpty() && !outcome.report().contains("Не выполнено"), "A step was skipped: " + outcome.report());
            h.assertTrue(outcome.report().contains("Поставил minecraft:chest: 1"), "The chest was not placed: " + outcome.report());
            h.assertTrue(outcome.report().contains("Сложил"), "The things were not put away: " + outcome.report());
            BlockPos placed = blockNear(h, Blocks.CHEST, h.absolutePos(mine), 5);
            h.assertTrue(placed != null, "No new chest by the mine: " + outcome.report());
            var into = (Container) h.getLevel().getBlockEntity(placed);
            h.assertTrue(countIn(contents(into), Items.BRICK) == 30, "The taken bricks are not in the new chest: " + summary(into));
            h.assertTrue(countIn(bag(c), Items.BRICK) == 0, "Bricks left in the bag: " + c.getLastTaskResult());
            h.assertTrue(countIn(bag(c), Items.OAK_LOG) == 0 && countIn(bag(c), Items.CHEST) == 0,
                    "The chest or the logs are still in the bag: " + c.getLastTaskResult());
            // A filler stack was held in a hand to free the cell the chest needed: it must be somewhere afterwards, in the
            // bag or in the chest, and never dropped — a lost stack cannot pass this test.
            h.assertTrue(countIn(bag(c), Items.DIRT) + countIn(contents(into), Items.DIRT) == 2,
                    "The stack held in a hand was lost: " + countIn(bag(c), Items.DIRT) + " dirt in the bag, "
                            + countIn(contents(into), Items.DIRT) + " in the chest: " + outcome.report());
        });
    }

    /**
     * Two steps of one order with a full bag: the first stack goes into the free hand, and once the cell it freed is taken
     * again the next step must take the other free hand instead of claiming the bag has room. Before the fix the second
     * step returned at once, so nothing was taken and the craft refused for space although the other hand was free.
     */
    @GameTest(template = "empty")
    public static void aFullBagFindsTheOtherFreeHandWhenOneAlreadyHoldsAStack(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        for (int slot = 0; slot < 27; slot++) c.setItem(slot, new ItemStack(FILLER[slot], 2));
        h.assertTrue(freeCells(bag(c)) == 0, "The bag is not full: " + freeCells(bag(c)));
        h.assertTrue(c.holdOneStackInHand(), "No stack was taken with both hands free");
        h.assertTrue(c.heldStackHands().equals(List.of("offhand")), "The stack is not in the free offhand: " + c.heldStackHands());
        h.assertTrue(freeCells(bag(c)) == 1, "The bag cell was not freed: " + freeCells(bag(c)));
        // What the craft made takes the freed cell back: the bag is full again and the other hand is still free.
        c.setItem(0, new ItemStack(Items.CHEST, 1));
        h.assertTrue(c.holdOneStackInHand(), "The other free hand was not used: " + c.heldStackHands());
        h.assertTrue(c.heldStackHands().equals(List.of("mainhand", "offhand")), "Only one hand holds a stack: " + c.heldStackHands());
        h.assertTrue(freeCells(bag(c)) == 1, "The second cell was not freed: " + freeCells(bag(c)));
        h.succeed();
    }

    /**
     * A stack held in a hand is the work's, not equipment: a save and a reload must put it back into the bag, not leave it
     * in the hand forever (where a later order would strand a second one beside it).
     */
    @GameTest(template = "empty")
    public static void aStackHeldInAHandComesBackAfterAReload(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        for (int slot = 0; slot < 27; slot++) c.setItem(slot, new ItemStack(FILLER[slot], 2));
        h.assertTrue(c.holdOneStackInHand(), "No stack was taken with both hands free");
        h.assertTrue(c.getOffhandItem().is(Items.DIRT) && countIn(bag(c), Items.DIRT) == 0,
                "The stack is not in the free offhand: " + c.getOffhandItem());
        var saved = new CompoundTag();
        c.addAdditionalSaveData(saved);
        var loaded = AICompanionMod.COMPANION.get().create(h.getLevel());
        loaded.readAdditionalSaveData(saved);
        h.assertTrue(countIn(bag(loaded), Items.DIRT) == 2, "The held stack did not come back into the bag: " + countIn(bag(loaded), Items.DIRT));
        h.assertTrue(loaded.getMainHandItem().isEmpty() && loaded.getOffhandItem().isEmpty(),
                "A stack stayed in a hand after the reload: " + loaded.getMainHandItem() + " " + loaded.getOffhandItem());
        h.succeed();
    }

    /**
     * A torch chain is made of coal and sticks: a full bag must hold some other stack, never the coal. The plan reads the
     * bag, so a coal taken into a hand is «не хватает «Уголь»» while he holds it — the coal must stay where the plan sees it.
     */
    @GameTest(template = "empty")
    public static void aFullBagKeepsTheCoalOfATorchChainInTheBag(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        // 25 stacks that are none of the torch chain's materials: the torch stack has to be made, so its cell is missing.
        Item[] plain = {Items.DIRT, Items.GRAVEL, Items.SAND, Items.FLINT, Items.ANDESITE, Items.DIORITE, Items.GRANITE,
                Items.TUFF, Items.CALCITE, Items.DEEPSLATE, Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
                Items.REDSTONE, Items.LAPIS_LAZULI, Items.CHARCOAL, Items.STRING, Items.FEATHER, Items.LEATHER, Items.BONE,
                Items.GUNPOWDER, Items.BRICK, Items.NETHER_BRICK, Items.AMETHYST_SHARD, Items.SLIME_BALL};
        c.setItem(0, new ItemStack(Items.COAL, 8));
        c.setItem(1, new ItemStack(Items.STICK, 4));
        for (int slot = 0; slot < plain.length; slot++) c.setItem(slot + 2, new ItemStack(plain[slot], 2));
        h.assertTrue(freeCells(bag(c)) == 0 && countIn(bag(c), Items.TORCH) == 0, "The bag is not full and torchless: " + freeCells(bag(c)));
        var crafted = c.craftHoldingIfTheBagIsFull("minecraft:torch", 1);
        h.assertTrue(crafted.success(), "The torches were refused although the coal and sticks are carried: " + crafted.message());
        h.assertTrue(countIn(bag(c), Items.TORCH) == 4, "No torches in the bag: " + crafted.message());
        h.assertTrue(countIn(bag(c), Items.COAL) == 7, "The coal left the bag for a hand: " + countIn(bag(c), Items.COAL) + " coal, hands " + c.heldStackHands());
        h.assertTrue(c.getOffhandItem().is(Items.DIRT), "The stack for the hand was not a filler: " + c.getOffhandItem() + " " + c.heldStackHands());
        h.succeed();
    }

    /**
     * A full bag, the wood for a chest, but no cell for the planks and no free hand to hold one stack in: the answer names
     * what is missing in plain words, the plan says which step failed, lists the rest as not done, and nothing claims the
     * storing happened.
     */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void aFullBagSaysWhatIsMissingForTheChestAndThePlanSaysWhatFailed(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        for (int slot = 0; slot < 26; slot++) c.setItem(slot, new ItemStack(FILLER[slot], 2));
        c.setItem(26, new ItemStack(Items.OAK_LOG, 3));
        // Both hands taken, as at a mine: a pickaxe and torches leave nothing to hold a stack in.
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH, 8));
        var plan = CompanionPlanRunner.run(List.of(
                        new PlannedAction("place_block", 0, 1, "minecraft:chest"),
                        new PlannedAction("store_items", 0, 1, "resources")),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-marker-full-bag", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            String report = outcome.report();
            h.assertTrue(report.contains("План остановлен: Поставил 0 из 1."), "The failed step is not named: " + report);
            h.assertTrue(report.contains("нет места") && report.toLowerCase(java.util.Locale.ROOT).contains("planks"),
                    "What is missing is not named: " + report);
            h.assertTrue(!report.contains("цепочки крафта"), "The craft chain is named for a chest: " + report);
            h.assertTrue(outcome.skipped().size() == 1 && "store_items".equals(outcome.skipped().getFirst().type()),
                    "The rest of the plan is not listed as not done: " + outcome.skipped());
            h.assertTrue(report.contains("Не выполнено: сложить вещи в сундук"), "The skipped step is not said: " + report);
            h.assertTrue(!report.contains("Сложил"), "A failed plan claimed the storing: " + report);
            h.assertTrue(!c.didLastTaskSucceed(), "The failed step claimed success: " + c.getLastTaskResult());
            h.assertTrue(countIn(bag(c), Items.OAK_LOG) == 3, "The materials were spent: " + c.getLastTaskResult());
            h.assertTrue(blockNear(h, Blocks.CHEST, c.blockPosition(), 5) == null, "A chest was placed after all: " + c.getLastTaskResult());
        });
    }

    /** Two half stacks of planks take two cells: merging them frees one, and the chest is made out of what they hold. */
    @GameTest(template = "empty")
    public static void mergingTwoPlanksStacksFreesTheCellTheChestNeeds(GameTestHelper h) {
        List<ItemStack> bag = fullBag(new ItemStack(Items.OAK_PLANKS, 20), new ItemStack(Items.OAK_PLANKS, 20));
        h.assertTrue(freeCells(bag) == 0 && planksStacks(bag) == 2, "The bag is not full of two half stacks: " + freeCells(bag));
        var plan = CompanionCrafting.plan(h.getLevel(), bag, "minecraft:chest", 1, false);
        h.assertTrue(plan.success(), "The chest was refused: " + plan.message());
        h.assertTrue(countIn(plan.inventory(), Items.CHEST) == 1, "No chest made: " + plan.message());
        h.assertTrue(planksStacks(plan.inventory()) == 1, "The two half stacks were not merged: " + plan.message());
        h.assertTrue(countIn(plan.inventory(), Items.OAK_PLANKS) == 28, "The planks do not add up: " + countIn(plan.inventory(), Items.OAK_PLANKS));
        h.assertTrue(plan.placeTable(), "The bench he had to make first is not set down: " + plan.message());
        h.assertTrue(plan.message().contains("Скрафтил 1 «Chest»"), "No plain answer: " + plan.message());
        h.succeed();
    }

    /** A chest already in the bag is set down as it is: with every cell taken and no wood, nothing is crafted. */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void aChestInTheBagIsSetDownInsteadOfBeingCrafted(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.CHEST, 1));
        for (int slot = 1; slot < 27; slot++) c.setItem(slot, new ItemStack(FILLER[slot - 1], 2));
        h.assertTrue(CompanionEvents.executeAction(player(h, c), "place_block", "minecraft:chest", 1).success(), "Order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && c.getLastTaskResult().contains("Поставил minecraft:chest: 1"), "Not placed: " + c.getLastTaskResult());
            h.assertTrue(!c.getLastTaskResult().contains("Скрафтил"), "The chest was crafted though he carried one: " + c.getLastTaskResult());
            h.assertTrue(countIn(bag(c), Items.CHEST) == 0, "The chest is still in the bag: " + c.getLastTaskResult());
            h.assertTrue(blockNear(h, Blocks.CHEST, c.blockPosition(), 6) != null, "No chest in the world: " + c.getLastTaskResult());
        });
    }

    /** A full bag and no wood at all: the answer names the logs, not «цепочку крафта», and it is a shortage. */
    @GameTest(template = "empty")
    public static void aChestShortOfWoodNamesTheLogsInsteadOfTheCraftChain(GameTestHelper h) {
        List<ItemStack> bag = fullBag();
        h.assertTrue(countIn(bag, Items.OAK_LOG) == 0 && freeCells(bag) == 0, "The bag is not full and woodless");
        var plan = CompanionCrafting.plan(h.getLevel(), bag, "minecraft:chest", 1, false);
        h.assertTrue(!plan.success() && plan.shortage(), "The shortage was not a shortage: " + plan.message());
        h.assertTrue(plan.message().contains("«Chest»") && plan.message().contains("не хватает"), "What is missing is not named: " + plan.message());
        h.assertTrue(plan.message().toLowerCase(java.util.Locale.ROOT).contains("log"), "The logs are not named: " + plan.message());
        h.assertTrue(!plan.message().contains("цепочки крафта"), "The craft chain is named: " + plan.message());
        h.succeed();
    }

    // ---- fixtures for the bag ----

    /** A bag with every cell taken: the extras first, then filler things that are not wood, planks or a chest. */
    private static List<ItemStack> fullBag(ItemStack... extras) {
        List<ItemStack> bag = new java.util.ArrayList<>();
        for (ItemStack extra : extras) bag.add(extra.copy());
        for (int at = 0; bag.size() < 27; at++) bag.add(new ItemStack(FILLER[at], 2));
        return bag;
    }

    private static List<ItemStack> bag(CompanionEntity c) {
        List<ItemStack> stacks = new java.util.ArrayList<>();
        for (int slot = 0; slot < c.getContainerSize(); slot++) stacks.add(c.getItem(slot).copy());
        return stacks;
    }

    private static List<ItemStack> contents(Container container) {
        List<ItemStack> stacks = new java.util.ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) stacks.add(container.getItem(slot));
        return stacks;
    }

    private static String summary(Container container) {
        return contents(container).stream().filter(stack -> !stack.isEmpty())
                .map(stack -> stack.getCount() + " " + stack.getHoverName().getString()).toList().toString();
    }

    private static int countIn(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static int freeCells(List<ItemStack> stacks) {
        return (int) stacks.stream().filter(ItemStack::isEmpty).count();
    }

    private static long planksStacks(List<ItemStack> stacks) {
        return stacks.stream().filter(stack -> stack.is(Items.OAK_PLANKS)).count();
    }

    /** The first block of this kind within a box around an absolute point, or null. */
    private static BlockPos blockNear(GameTestHelper h, Block block, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius)))
            if (h.getLevel().hasChunkAt(pos) && h.getLevel().getBlockState(pos).is(block)) return pos.immutable();
        return null;
    }

    // ---- the settings page at every GUI size the auto scale picks ----

    /**
     * 320×240 and 384×288 are reachable by the auto scale. Before the fix «Метки» sat at 152..222 inside «Вещи после
     * смерти» 100..250 at 320, and at 216..286 inside it at 384: both entries are now on a second header row below
     * 418 px, so no entry covers another at any of the sizes, and none runs off the screen.
     */
    @GameTest(template = "empty")
    public static void settingsHeaderEntriesNeverCoverEachOtherAtAnyGuiSize(GameTestHelper h) {
        int[][] sizes = {{320, 240}, {384, 288}, {418, 314}, {640, 360}, {854, 480}, {1920, 1080}};
        for (int[] size : sizes) {
            int width = size[0], height = size[1];
            String at = width + "×" + height + ": ";
            CompanionSettingsLayout.Header header = CompanionSettingsLayout.header(width);
            List<CompanionSettingsLayout.Box> entries = header.entries();
            for (int a = 0; a < entries.size(); a++)
                for (int b = a + 1; b < entries.size(); b++)
                    h.assertTrue(!entries.get(a).overlaps(entries.get(b)),
                            at + "header entries " + a + " and " + b + " overlap: " + entries.get(a) + " " + entries.get(b));
            for (CompanionSettingsLayout.Box entry : entries)
                h.assertTrue(entry.inside(width, height), at + "a header entry is off the screen: " + entry);
            // The title is drawn only in the span the entries leave, so it can never reach one of them.
            for (CompanionSettingsLayout.Box entry : entries)
                h.assertTrue(header.titleTo() <= entry.x() || header.titleFrom() >= entry.right(),
                        at + "the title span reaches an entry: " + header.titleFrom() + ".." + header.titleTo() + " " + entry);
            // Two rows below 418 px, one row from it: the narrow window stacks the entries instead of shrinking them apart.
            boolean stacked = header.behavior().y() > header.memory().y();
            h.assertTrue(stacked == (width < CompanionSettingsLayout.ONE_ROW_MIN_WIDTH),
                    at + "the header rows are not the ones this width needs: stacked=" + stacked);
            h.assertTrue(header.firstY() >= entries.stream().mapToInt(CompanionSettingsLayout.Box::bottom).max().orElse(0) + 8,
                    at + "the first field row reaches the header: " + header.firstY());
        }
        h.succeed();
    }

    /**
     * A label must not reach the field above it: 37 px is the least pitch that leaves the 9-px glyphs clear of a 20-px
     * field, and before the fix the pitch floored at 28, so «Характер компаньона» painted over «ID голоса Inworld» at a
     * GUI height of 240 and 288. A height that cannot hold every row gets pages instead of overlaps, and every row is on
     * one of them.
     */
    @GameTest(template = "empty")
    public static void settingsFieldRowsStayClearOfTheirLabelsTheNoteAndTheFooter(GameTestHelper h) {
        int[][] sizes = {{320, 240}, {384, 288}, {418, 314}, {640, 360}, {854, 480}, {1920, 1080}};
        for (int[] size : sizes) {
            int width = size[0], height = size[1];
            String at = width + "×" + height + ": ";
            h.assertTrue(CompanionSettingsLayout.rowGap(height) >= CompanionSettingsLayout.ROW_PITCH_MIN,
                    at + "the pitch is under the label and field it must hold: " + CompanionSettingsLayout.rowGap(height));
            int perPage = CompanionSettingsLayout.rowsPerPage(width, height);
            int pages = CompanionSettingsLayout.pages(width, height);
            h.assertTrue(pages >= 1 && perPage * pages >= CompanionSettingsLayout.ROW_COUNT,
                    at + "the pages cannot hold every row: " + perPage + "×" + pages);
            CompanionSettingsLayout.Footer footer = CompanionSettingsLayout.footer(width, height, pages);
            List<CompanionSettingsLayout.Box> buttons = footer.boxes();
            for (int a = 0; a < buttons.size(); a++) {
                h.assertTrue(buttons.get(a).inside(width, height), at + "a footer button is off the screen: " + buttons.get(a));
                for (int b = a + 1; b < buttons.size(); b++)
                    h.assertTrue(!buttons.get(a).overlaps(buttons.get(b)),
                            at + "footer buttons overlap: " + buttons.get(a) + " " + buttons.get(b));
            }
            int[] seen = new int[CompanionSettingsLayout.ROW_COUNT];
            for (int page = 0; page < pages; page++) {
                List<CompanionSettingsLayout.Row> rows = CompanionSettingsLayout.page(width, height, page);
                h.assertTrue(!rows.isEmpty(), at + "page " + page + " has no rows");
                for (CompanionSettingsLayout.Row row : rows) {
                    seen[row.index()]++;
                    CompanionSettingsLayout.Box field = row.field();
                    CompanionSettingsLayout.Box label = CompanionSettingsLayout.label(width, row);
                    h.assertTrue(!label.overlaps(field), at + "row " + row.index() + " label covers its field: " + label + " " + field);
                    h.assertTrue(field.bottom() <= CompanionSettingsLayout.contentBottom(height),
                            at + "row " + row.index() + " reaches the note: " + field.bottom());
                    h.assertTrue(field.bottom() <= CompanionSettingsLayout.noteY(height), at + "row " + row.index() + " reaches the note");
                    for (CompanionSettingsLayout.Box button : footer.boxes())
                        h.assertTrue(!field.overlaps(button), at + "row " + row.index() + " reaches a footer button: " + field + " " + button);
                    h.assertTrue(field.x() >= 0 && field.right() <= width, at + "row " + row.index() + " is off the screen: " + field);
                    h.assertTrue(CompanionSettingsLayout.fieldRight(width) <= width,
                            at + "the second column runs off the screen: " + CompanionSettingsLayout.fieldRight(width));
                }
                for (int a = 0; a < rows.size(); a++) {
                    for (int b = a + 1; b < rows.size(); b++) {
                        h.assertTrue(!rows.get(a).field().overlaps(rows.get(b).field()),
                                at + "two fields of a page overlap: " + rows.get(a).field() + " " + rows.get(b).field());
                        h.assertTrue(rows.get(b).labelY() >= rows.get(a).field().bottom(),
                                at + "the label of row " + rows.get(b).index() + " reaches the field above it: "
                                        + rows.get(b).labelY() + " after " + rows.get(a).field().bottom());
                    }
                }
            }
            for (int row = 0; row < CompanionSettingsLayout.ROW_COUNT; row++)
                h.assertTrue(seen[row] == 1, at + "row " + row + " is on " + seen[row] + " pages");
        }
        h.succeed();
    }

    // ---- fixtures ----

    /** One tool call of the model's answer, as the provider hands it over: the plan JSON is the arguments string. */
    private static JsonObject toolCall(String arguments) {
        JsonObject function = new JsonObject();
        function.addProperty("name", "queue_companion_plan");
        function.addProperty("arguments", arguments);
        JsonObject call = new JsonObject();
        call.add("function", function);
        JsonArray calls = new JsonArray();
        calls.add(call);
        JsonObject message = new JsonObject();
        message.addProperty("content", "");
        message.add("tool_calls", calls);
        return message;
    }

    /** The arrivals at one marker, or at any marker, in a plan's report. */
    private static int arrivals(String report) {
        return (int) report.lines().filter(line -> line.contains("Пришёл к метке «")).count();
    }

    private static int arrivals(String report, String name) {
        return (int) report.lines().filter(line -> line.contains("Пришёл к метке «" + name + "»")).count();
    }

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

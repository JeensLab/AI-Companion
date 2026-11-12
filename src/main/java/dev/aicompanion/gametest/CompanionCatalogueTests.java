package dev.aicompanion.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.entity.CompanionVillageBuildings;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The closed catalogue of village buildings: each is a template of the running game he can build (size, blocks), it is asked for
 * by its name or its kind without the model, and the model is told the same names and refused the rest by what is like them.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCatalogueTests {
    @GameTest(template = "empty")
    public static void everyBuildingOfTheCatalogueIsAFittingTemplateOfTheGame(GameTestHelper h) {
        Set<String> names = new HashSet<>();
        for (var building : CompanionVillageBuildings.all()) {
            String at = "«" + building.name() + "»: ";
            h.assertTrue(names.add(building.name().toLowerCase().replace('ё', 'е')), at + "name is not unique");
            var template = h.getLevel().getServer().getStructureManager().get(building.template());
            h.assertTrue(template.isPresent(), at + "the game has no template " + building.template());
            CompoundTag tag = template.get().save(new CompoundTag());
            ListTag size = tag.getList("size", Tag.TAG_INT);
            h.assertTrue(size.getInt(0) == building.width() && size.getInt(2) == building.depth() && size.getInt(1) == building.height(),
                    at + "the template is " + size.getInt(0) + "×" + size.getInt(2) + "×" + size.getInt(1) + ", the catalogue says " + building.width() + "×" + building.depth() + "×" + building.height());
            h.assertTrue(building.width() * building.depth() * building.height() <= CompanionVillageBuildings.MAX_VOLUME && building.height() <= CompanionVillageBuildings.MAX_HEIGHT, at + "over the ceiling");
            h.assertTrue(building.biome().matches("plains|desert|savanna|taiga|snowy"), at + "unknown village type " + building.biome());
            h.assertTrue("дом".equals(building.kind()) || CompanionVillageBuildings.named(building.kind(), false) != null, at + "its kind " + building.kind() + " is not one of the kinds");
            for (Tag entry : tag.getList("palette", Tag.TAG_COMPOUND)) {
                BlockState state = NbtUtils.readBlockState(h.getLevel().holderLookup(Registries.BLOCK), (CompoundTag) entry);
                if (state.isAir() || state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.JIGSAW)) continue;
                h.assertTrue(state.getBlock().asItem() != Items.AIR, at + state.getBlock() + " has no item");
                h.assertTrue("minecraft".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace()) && state.getBlock().defaultDestroyTime() >= 0
                        && !state.is(Blocks.SPAWNER), at + state.getBlock() + " is not a survival block of the game");
                h.assertTrue(state.getFluidState().isEmpty() && !(state.getBlock() instanceof LiquidBlock) && !state.is(Blocks.WATER_CAULDRON) && !state.is(Blocks.LAVA_CAULDRON),
                        at + state.getBlock() + " holds a fluid");
                h.assertTrue(!(state.getBlock() instanceof FarmBlock) && !(state.getBlock() instanceof CropBlock) && !(state.getBlock() instanceof StemBlock)
                        && !(state.getBlock() instanceof FlowerPotBlock), at + state.getBlock() + " needs water or a pot");
            }
            // «Построй <name>» is that building and not the plank house, which keeps «дом», «маленький дом», «небольшой дом», «деревянный дом».
            var asked = CompanionWorkCommands.parse("Олег, построй " + building.name(), "Олег");
            h.assertTrue(asked != null && "build_house".equals(asked.type()) && building.name().equals(asked.item()), at + "the direct command does not name it");
        }
        for (String plank : new String[]{"дом", "маленький дом", "небольшой дом", "деревянный дом"})
            h.assertTrue(CompanionWorkCommands.parse("построй " + plank, "Олег").item().isEmpty(), "«" + plank + "» is not the plank house any more");
        h.assertTrue(CompanionVillageBuildings.modelText().length() < CompanionVillageBuildings.MODEL_TEXT_LIMIT,
                "What the model is told of the catalogue is " + CompanionVillageBuildings.modelText().length() + " characters");
        h.succeed();
    }

    private static Holder<Biome> biome(GameTestHelper h, ResourceKey<Biome> key) { return h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key); }

    @GameTest(template = "empty")
    public static void aKindIsBuiltWithoutTheModelAsTheOneOfThePlaceAndAnUnknownRequestIsLeftToTheModel(GameTestHelper h) {
        var said = CompanionWorkCommands.parse("Олег, построй кузницу", "Олег");
        h.assertTrue(said != null && "build_house".equals(said.type()) && "кузница".equals(said.item()), "«построй кузницу» is not the kind smithy");
        h.assertTrue(CompanionWorkCommands.parse("построй храм", "Олег") != null, "«построй храм» was not understood");
        var plains = CompanionVillageBuildings.resolve("кузница", biome(h, Biomes.PLAINS));
        var savanna = CompanionVillageBuildings.resolve("кузница", biome(h, Biomes.SAVANNA));
        var taiga = CompanionVillageBuildings.resolve("кузница", biome(h, Biomes.TAIGA));
        var desert = CompanionVillageBuildings.resolve("кузница", biome(h, Biomes.DESERT));
        var elsewhere = CompanionVillageBuildings.resolve("кузница", biome(h, Biomes.DEEP_OCEAN));
        h.assertTrue("plains".equals(plains.biome()) && "taiga".equals(taiga.biome()), "The smithy does not follow the place: " + plains.name() + ", " + taiga.name());
        // No smithy of the desert or the savanna in the catalogue, nor out at sea: the first of the kind, the same each time.
        h.assertTrue("plains".equals(desert.biome()) && desert.equals(elsewhere) && savanna.equals(elsewhere) && elsewhere.equals(CompanionVillageBuildings.resolve("кузница", biome(h, Biomes.DEEP_OCEAN)))
                && elsewhere.equals(CompanionVillageBuildings.resolve("кузница", null)) && "кузница".equals(elsewhere.kind()), "Outside any village type it is not the same first one every time");
        h.assertTrue(CompanionVillageBuildings.resolve("пустынный дом", biome(h, Biomes.TAIGA)).name().equals("пустынный дом"), "A name was changed to suit the place");
        h.assertTrue(CompanionVillageBuildings.resolve("замок", biome(h, Biomes.PLAINS)) == null, "A castle resolved to something");
        // What the catalogue does not have is not swallowed: it stays the model's, as before.
        for (String other : new String[]{"построй замок", "построй кузницу для эльфов", "построй огромную башню", "построй кузницу из золота", "построй библиотеку"})
            h.assertTrue(CompanionWorkCommands.parse(other, "Олег") == null, "«" + other + "» was taken by the direct command");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theModelIsToldTheCatalogueAndItsPlanIsCheckedAgainstIt(GameTestHelper h) {
        String prompt = OpenRouterClient.buildSystemPrompt(OpenRouterClient.buildPersona("Весёлый напарник.", "Гоша", Map.of()), true, "лучший друг");
        for (var building : CompanionVillageBuildings.all())
            h.assertTrue(prompt.contains(building.name() + " " + building.width() + "×" + building.depth() + "×" + building.height()), "The prompt lacks «" + building.name() + "»");
        h.assertTrue(prompt.contains("resume") && prompt.contains("5×5"), "The prompt does not say «resume» and the 5×5 house");
        for (var building : CompanionVillageBuildings.all()) {
            var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\",\"item\":\"" + building.name().toUpperCase() + "\"}]"));
            h.assertTrue(plan.getFirst().item().equals(building.name()), "«" + building.name() + "» is not taken as the catalogue spells it");
        }
        h.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\",\"item\":\"resume\"}]")).getFirst().item().equals("resume"), "resume refused");
        h.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\"}]")).getFirst().item().isEmpty(), "The plank house refused");
        // A kind is the model's shortcut and still the catalogue's: it names no building outside it.
        h.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\",\"item\":\"кузница\"}]")).getFirst().item().equals("кузница"), "A kind refused");
        String like = refusal(h, "кузница дракона"), nothing = refusal(h, "замок дракона");
        var smithies = CompanionVillageBuildings.all().stream().filter(b -> "кузница".equals(b.kind())).toList();
        h.assertTrue(like.contains("Похожие") && like.contains(smithies.getFirst().name()) && !like.contains(CompanionVillageBuildings.all().getLast().name()) && like.length() < 400, "No few similar ones: " + like);
        h.assertTrue(nothing.contains("храм") && nothing.contains("кузница") && nothing.length() < 400, "No kinds named: " + nothing);
        h.succeed();
    }

    /** The refusal the player reads when the model's plan names a building the catalogue lacks (the plan is rejected whole, as today). */
    private static String refusal(GameTestHelper h, String invented) {
        JsonObject call = JsonParser.parseString("{\"function\":{\"name\":\"queue_companion_plan\",\"arguments\":\"[{\\\"action\\\":\\\"wait\\\"},{\\\"action\\\":\\\"build_house\\\",\\\"item\\\":\\\"" + invented + "\\\"}]\"}}").getAsJsonObject();
        JsonObject message = new JsonObject();
        message.addProperty("content", "");
        var calls = new com.google.gson.JsonArray();
        calls.add(call);
        message.add("tool_calls", calls);
        try { CompanionPlanCodec.decode(message); } catch (IllegalStateException refused) { return refused.getMessage(); }
        h.fail("A building outside the catalogue was accepted: " + invented);
        return "";
    }
}

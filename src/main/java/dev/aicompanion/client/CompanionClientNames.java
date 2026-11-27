package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionItemLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The Russian names of the game's own things, whatever language the client shows. The mod speaks Russian and its player
 * says «тыкву», «забор», «калитку»; a client whose language is en_us shows «Pumpkin», so every word of the player's own
 * language named nothing at all (owner, 27.11: «добудь тыкву» — «имени нет в игре», «добудь вишневый забор» the day
 * before). The names are the game's own: the ru_ru.json of every namespace of its resource packs (a mod's own thing
 * included), read once; an item the game has no Russian name for keeps the name the client shows.
 *
 * Outside the client (a dedicated server, the GameTest server) there is no language pack to read: the names are the ones
 * the game shows, and a caller with its own stand-in passes it ({@link CompanionItemLookup#itemFor(String, Function)}).
 */
public final class CompanionClientNames {
    private CompanionClientNames() { }

    private static volatile Function<Item, String> names;

    /** The name of an item in the player's own language: the game's Russian name, else the one the client shows. */
    public static Function<Item, String> names() {
        Function<Item, String> known = names;
        if (known != null) return known;
        // The client's packs are not read before the game has them: the names the game shows answer until then.
        Function<Item, String> loaded = load();
        if (loaded == null) return CompanionItemLookup.GAME_NAMES;
        return names = loaded;
    }

    /** The reader of the Russian names, or null while the client's resource packs are not there yet. */
    private static Function<Item, String> load() {
        if (FMLEnvironment.dist != Dist.CLIENT) return CompanionItemLookup.GAME_NAMES;
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) return null;
        Map<String, String> russian;
        try { russian = russianNames(minecraft.getResourceManager()); } catch (IOException | RuntimeException unread) { russian = Map.of(); }
        Map<String, String> byKey = russian;
        return item -> {
            String name = byKey.get(item.getDescriptionId());
            return name != null ? name : new ItemStack(item).getHoverName().getString();
        };
    }

    /** The ru_ru.json of every namespace of the client's packs: the game's own Russian names of its items. */
    private static Map<String, String> russianNames(net.minecraft.server.packs.resources.ResourceManager resources) throws IOException {
        Map<String, String> names = new HashMap<>();
        var found = resources.listResources("lang", id -> id.getPath().equals("ru_ru.json"));
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                var json = com.google.gson.JsonParser.parseReader(reader);
                if (!json.isJsonObject()) continue;
                for (var line : json.getAsJsonObject().entrySet())
                    if (line.getValue().isJsonPrimitive()) names.put(line.getKey(), line.getValue().getAsString());
            }
        }
        // A namespace whose lang file is broken is no reason to lose every name of the game: the rest still answer.
        return names;
    }
}

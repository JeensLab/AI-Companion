package dev.aicompanion.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionMusicLibrary;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The client's dynamic resource pack for the player's guitar folder (the owner's ruling, 13.11: the recordings are the player's own
 * .ogg files, only a couple of standard ones stay in the JAR). The game's sound engine knows a sound only from a resource pack, so
 * this pack serves a sounds.json generated from the folder — every file there becomes a sound event — and the files themselves. It is
 * always active (a required pack at the top) and is rebuilt on every resource reload, so the folder is read at startup and again
 * after «/companion music».
 */
public final class CompanionMusicPack implements PackResources {
    static final String ID = "aicompanion_music";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAMESPACE = AICompanionMod.MOD_ID;
    private static final String README = """
            Музыка Олега у костра.
            Положите сюда записи в Ogg Vorbis (.ogg, моно): каждую из них Олег сможет сыграть.
            Настроение записи: рядом файл <имя>.mood со словом sad, calm или happy — или [sad], [calm], [happy] в имени файла.
            Добавили записи — введите в чат /companion music, чтобы игра их прочитала.
            """;

    private final PackLocationInfo location;
    /** The pack's resource paths of the folder's recordings, by the file they are served from. */
    private final Map<String, Path> files = new LinkedHashMap<>();
    private final byte[] sounds;
    private final byte[] meta;

    public CompanionMusicPack(PackLocationInfo location) { this(location, CompanionMusicLibrary.folder()); }

    /** The same pack over a given folder: the tests build it over a folder of their own. */
    public CompanionMusicPack(PackLocationInfo location, Path folder) {
        this.location = location;
        ensureFolder(folder);
        JsonObject root = new JsonObject();
        for (CompanionMusicLibrary.Track track : CompanionMusicLibrary.scan(folder)) {
            String path = "sounds/" + track.soundId().substring(NAMESPACE.length() + 1) + ".ogg";
            files.put(path, folder.resolve(track.file()));
            JsonObject sound = new JsonObject();
            sound.addProperty("name", track.soundId());
            sound.addProperty("stream", true);
            JsonArray sounds = new JsonArray();
            sounds.add(sound);
            JsonObject entry = new JsonObject();
            entry.add("sounds", sounds);
            entry.addProperty("subtitle", "subtitles.aicompanion.guitar");
            root.add(track.soundId().substring(NAMESPACE.length() + 1), entry);
        }
        this.sounds = root.toString().getBytes(StandardCharsets.UTF_8);
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", format);
        pack.addProperty("description", "AI Companion: музыка из папки");
        JsonObject description = new JsonObject();
        description.add("pack", pack);
        this.meta = description.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** The folder is made on the first start, with a short note in it, so the player has where to put the recordings. */
    private static void ensureFolder(Path folder) {
        try {
            if (Files.isDirectory(folder)) return;
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("README.txt"), README, StandardCharsets.UTF_8);
        } catch (IOException error) {
            LOGGER.warn("AI Companion: cannot make the guitar folder {}: {}", folder, error.toString());
        }
    }

    @Override public IoSupplier<InputStream> getRootResource(String... path) {
        return path.length == 1 && "pack.mcmeta".equals(path[0]) ? () -> new ByteArrayInputStream(meta) : null;
    }

    @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        if (type != PackType.CLIENT_RESOURCES || !NAMESPACE.equals(id.getNamespace())) return null;
        if ("sounds.json".equals(id.getPath())) return () -> new ByteArrayInputStream(sounds);
        Path file = files.get(id.getPath());
        return file == null ? null : IoSupplier.create(file);
    }

    @Override public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !NAMESPACE.equals(namespace) || !(path.isEmpty() || "sounds".equals(path))) return;
        for (Map.Entry<String, Path> entry : files.entrySet())
            output.accept(ResourceLocation.fromNamespaceAndPath(NAMESPACE, entry.getKey()), IoSupplier.create(entry.getValue()));
    }

    @Override public Set<String> getNamespaces(PackType type) { return Set.of(NAMESPACE); }

    @Override @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        if (serializer != PackMetadataSection.TYPE) return null;
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        return (T) new PackMetadataSection(Component.literal("AI Companion: музыка из папки"), format, Optional.empty());
    }

    @Override public PackLocationInfo location() { return location; }

    @Override public void close() { }
}

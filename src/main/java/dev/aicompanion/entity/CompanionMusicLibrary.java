package dev.aicompanion.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.util.RandomSource;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What he plays by the fire (the owner's ruling, 13.11): the player's own folder of recordings, not a fixed set baked into the JAR —
 * no music ships in the mod at all. Every .ogg in config/aicompanion/music can be played; while that folder has none, he plays
 * standard Minecraft music instead ({@link CompanionTunes}), and nothing is said about it. A recording's mood is the digit its file
 * name starts with, 1 (sad) to 9 (happy): 1–4 is the calm kind («сыграй грустное»), 6–9 the cheerful one («повеселее»), and 5 is
 * neither; so both requests can pick a recording of that kind. With no such recording he honestly plays any.
 *
 * <p>No client classes here: the server scans the same folder to choose the tune, and the client's own resource pack
 * (client/CompanionMusicPack) serves these files to the game's sound engine by the sound ids made here. The length of a recording is
 * read from its Ogg Vorbis pages, because the evening goes on to the next tune by that length.
 */
public final class CompanionMusicLibrary {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The player's folder, beside the game's config: config/aicompanion/music. */
    public static final String FOLDER = "aicompanion/music";
    /**
     * The note the mod keeps in the folder, rewritten on every start so it always matches this build: what to put there, how the
     * mood is written, and that on a server the same recordings are needed on both sides.
     */
    public static final String FOLDER_README = """
            Музыка Олега у костра.
            Положите сюда записи в Ogg Vorbis (.ogg, моно): каждую из них Олег сможет сыграть.
            Настроение записи — число в начале имени файла, от 1 (самая грустная) до 9 (самая весёлая):
            1–4 — спокойная или грустная, 5 — середина, 6–9 — весёлая. Например, 2_romance.ogg.
            Папка читается при запуске игры и при входе в мир; добавили записи на ходу — нажмите F3+T.
            На сервере записи нужны и серверу, и клиенту: сервер по своей папке выбирает мелодию, клиент по своей её играет,
            поэтому одинаковый набор должен лежать в config/aicompanion/music и на сервере, и у каждого игрока.
            """;
    /** How a recording may be tagged for «сыграй грустное» (calm and sad) or «повеселее» (cheerful). */
    public enum Mood { CALM, CHEERFUL }

    /** One playable recording: its file, the sound event the client resolves for it, the title he gives it, its length and its mood.
     *  The file is empty for the vanilla fallback, which has no file of the player's. */
    public record Track(String file, String soundId, String title, int ticks, Mood mood) { }

    /** A tune chosen for the next play, and whether it actually fits the kind the player asked for. */
    public record Pick(Track track, boolean moodMatched) { }

    private CompanionMusicLibrary() { }

    /** Another folder to read instead of the player's (the GameTests point it at an empty one); null means the player's folder. */
    private static Path override;

    /** The folder the player fills, or the one the tests read. */
    public static Path folder() { return override != null ? override : FMLPaths.CONFIGDIR.get().resolve(FOLDER); }

    /**
     * Reads recordings from another folder from now on, or from the player's folder again with null. Only a test that means it calls
     * this, and only for the time of its batch: the player's folder is the default everywhere, so loading a test class never changes
     * what the game plays.
     */
    public static void readFrom(Path dir) { override = dir; }

    /** What can be played now: the player's folder when it has recordings, standard Minecraft music while it is empty. */
    public static List<Track> available() {
        List<Track> folder = scan(folder());
        return folder.isEmpty() ? fallback() : folder;
    }

    /**
     * The standard Minecraft music for the fallback while the folder is empty (CompanionTunes): the same kind of track, with the
     * vanilla sound id the client resolves and no file of the player's.
     */
    public static List<Track> fallback() {
        List<Track> tracks = new ArrayList<>();
        for (CompanionTunes.Tune tune : CompanionTunes.ALL)
            tracks.add(new Track("", tune.soundId(), tune.title(), tune.ticks(), tune.cheerful() ? Mood.CHEERFUL : Mood.CALM));
        return List.copyOf(tracks);
    }

    /**
     * Every .ogg recording in a folder, by its file name, with the sound id its file gets, its title, its length in ticks and its
     * mood. A missing folder is simply empty, and says so once; files that are not readable Ogg Vorbis are skipped with a line
     * each. A scan is asked on every tune and on every resource-pack rebuild, so a line is said once per change, not every call.
     */
    public static List<Track> scan(Path dir) {
        if (!Files.isDirectory(dir)) {
            say(dir, "the guitar folder " + dir + " is not there — the standard music sounds instead");
            return List.of();
        }
        List<Path> files;
        try (var listing = Files.list(dir)) {
            files = listing.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException error) {
            warn(dir.toAbsolutePath() + "|unreadable", "cannot read the guitar folder " + dir + ": " + error);
            return List.of();
        }
        List<Track> tracks = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            String base = name.substring(0, name.length() - 4);
            int ticks = ticks(file);
            if (ticks < 20) {
                warn(file.toAbsolutePath().toString(), name + " is not a readable Ogg Vorbis recording, skipped");
                continue;
            }
            tracks.add(new Track(name, "aicompanion:guitar_folder/" + unique(clean(base), keys), title(base), ticks, mood(base)));
        }
        // What was found, in one line: the folder is the player's own, and a scan that comes back empty is why the vanilla music plays.
        say(dir, "the guitar folder " + dir + " holds " + tracks.size() + " playable recording(s)");
        return List.copyOf(tracks);
    }

    /** The last line said about each folder, so a repeated scan does not repeat it. */
    private static final java.util.Map<String, String> SAID = new java.util.concurrent.ConcurrentHashMap<>();
    /** Folders and files already warned about, said once each. */
    private static final Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Says a line about a folder once per change: the folder is read on every tune and every pack rebuild. */
    private static void say(Path dir, String line) {
        if (line.equals(SAID.put(dir.toAbsolutePath().toString(), line))) return;
        LOGGER.info("AI Companion: {}", line);
    }

    /** Warns once about a folder or a file that could not be read. */
    private static void warn(String key, String line) {
        if (WARNED.add(key)) LOGGER.warn("AI Companion: {}", line);
    }

    /**
     * The tune for the next play: one of the kind asked for («calm» — sad or calm, «cheerful») when the recordings carry that mood,
     * otherwise any of them with {@code moodMatched} false so he can say so; «next» and no request take any, never the one playing.
     * A recording of the asked kind that is playing now is played again when it is the only one of that kind: asking for the mood
     * restarts it rather than switching to a tune of the wrong kind.
     */
    public static Pick pick(List<Track> tracks, String wanted, String avoidSoundId, RandomSource random) {
        if (tracks.isEmpty()) return null;
        Mood mood = moodWanted(wanted);
        if (mood != null) {
            List<Track> tagged = new ArrayList<>();
            for (Track track : tracks) if (track.mood() == mood) tagged.add(track);
            if (!tagged.isEmpty()) {
                List<Track> fresh = new ArrayList<>();
                for (Track track : tagged) if (!track.soundId().equals(avoidSoundId)) fresh.add(track);
                List<Track> pool = fresh.isEmpty() ? tagged : fresh;
                return new Pick(pool.get(random.nextInt(pool.size())), true);
            }
        }
        List<Track> fresh = new ArrayList<>();
        for (Track track : tracks) if (!track.soundId().equals(avoidSoundId)) fresh.add(track);
        List<Track> pool = fresh.isEmpty() ? tracks : fresh;
        return new Pick(pool.get(random.nextInt(pool.size())), mood == null);
    }

    /** Whether any recording fits the kind asked for, so the answer to «сыграй грустное» can be honest before a tune starts. */
    public static boolean hasMood(List<Track> tracks, String wanted) {
        Mood mood = moodWanted(wanted);
        if (mood == null) return true;
        for (Track track : tracks) if (track.mood() == mood) return true;
        return false;
    }

    /** The mood a request names: calm (sad too) or cheerful; «next» and no request name none. */
    private static Mood moodWanted(String wanted) {
        if ("calm".equals(wanted)) return Mood.CALM;
        if ("cheerful".equals(wanted)) return Mood.CHEERFUL;
        return null;
    }

    /**
     * The mood a recording carries: the digit its file name starts with, 1 (sad) to 9 (happy). 1–4 is the calm kind, 6–9 the
     * cheerful one; 5 and a name that starts with no digit (or with a longer number, such as a year) carry none.
     */
    private static Mood mood(String base) {
        int scale = scale(base);
        if (scale == 0 || scale == 5) return null;
        return scale < 5 ? Mood.CALM : Mood.CHEERFUL;
    }

    /** The digit 1–9 a file name starts with — its place on the sad–happy scale — or 0 when it starts with none or a longer number. */
    private static int scale(String base) {
        if (base.isEmpty() || base.charAt(0) < '1' || base.charAt(0) > '9') return 0;
        if (base.length() > 1 && Character.isDigit(base.charAt(1))) return 0;
        return base.charAt(0) - '0';
    }

    /** The title he says: the file name without its mood digit, underscores as spaces, the first letter raised. */
    static String title(String base) {
        String cleaned = (scale(base) == 0 ? base : base.substring(1))
                .replaceFirst("^[ _.-]+", "").replace('_', ' ').strip().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) cleaned = base.strip();
        return cleaned.isEmpty() ? base : cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
    }

    /** A file name as a resource path: lower case, only what a sound id allows, not empty. */
    static String clean(String base) {
        String key = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_").replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return key.isEmpty() ? "track" : key;
    }

    /** Two files may clean to the same key: the later ones get a number, so both stay playable. */
    private static String unique(String key, Set<String> taken) {
        String candidate = key;
        for (int number = 2; !taken.add(candidate); number++) candidate = key + "_" + number;
        return candidate;
    }

    /**
     * The length of an Ogg Vorbis recording in ticks, read from its identification header (sample rate) and its last page (granule
     * position, the samples played); -1 when the file is not readable Ogg Vorbis.
     */
    static int ticks(Path file) {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size < 64) return -1;
            ByteBuffer head = ByteBuffer.allocate((int) Math.min(64 * 1024, size));
            readFully(channel, head, 0);
            byte[] start = head.array();
            int header = indexOf(start, new byte[]{1, 'v', 'o', 'r', 'b', 'i', 's'}, 0);
            // A file that ends right after the marker is no readable header: the sample rate would lie outside it.
            if (header < 0 || header + 16 > start.length) return -1;
            long rate = ByteBuffer.wrap(start, header + 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
            if (rate <= 0) return -1;
            int tailLength = (int) Math.min(64 * 1024, size);
            ByteBuffer tail = ByteBuffer.allocate(tailLength);
            readFully(channel, tail, size - tailLength);
            byte[] end = tail.array();
            long granule = -1;
            for (int at = indexOf(end, new byte[]{'O', 'g', 'g', 'S'}, 0); at >= 0 && at + 14 <= end.length;
                 at = indexOf(end, new byte[]{'O', 'g', 'g', 'S'}, at + 4)) {
                long samples = ByteBuffer.wrap(end, at + 6, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                if (samples >= 0) granule = samples;
            }
            if (granule <= 0) return -1;
            return (int) Math.round(granule * 20.0 / rate);
        } catch (IOException error) {
            return -1;
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer, long from) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, from + buffer.position());
            if (read < 0) return;
        }
    }

    private static int indexOf(byte[] data, byte[] what, int from) {
        outer:
        for (int i = Math.max(0, from); i <= data.length - what.length; i++) {
            for (int j = 0; j < what.length; j++) if (data[i + j] != what[j]) continue outer;
            return i;
        }
        return -1;
    }
}

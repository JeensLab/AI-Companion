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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What he plays by the fire (the owner's ruling, 13.11): the player's own folder of recordings, not a fixed set baked into the JAR —
 * no music ships in the mod at all. Every Ogg Vorbis recording in config/aicompanion/music and its subfolders can be played, because
 * the game's sound engine reads Ogg Vorbis only; while that folder has none, he plays standard Minecraft music instead
 * ({@link CompanionTunes}), and nothing is said about it. A recording's mood is the integer its file name starts with, 0 (the
 * saddest) to 999 (the happiest): the number is how sad or cheerful the recording is, and the requests move along that scale —
 * «самую грустную» takes the lowest number, «самую весёлую» the highest, «погрустнее» the nearest lower, «повеселее» the nearest
 * higher. A name that starts with no number, or with a number outside 0–999 (a year, say), carries no mood; with no recording of the
 * asked kind he honestly plays any other.
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
            Можно раскладывать записи по подпапкам — Олег читает всю папку целиком.
            Настроение записи — целое число в начале имени файла, от 0 (самая грустная) до 999 (самая весёлая).
            Число остаётся в имени файла, но в название, которое Олег говорит, не попадает. Например, 150_blue-blood.ogg.
            Папка читается при запуске игры, при входе в мир и заново на каждую просьбу сыграть другую, повеселее или погрустнее:
            добавили записи на ходу — просто попросите другую, перезапуск не нужен.
            На сервере записи нужны и серверу, и клиенту: сервер по своей папке выбирает мелодию, клиент по своей её играет,
            поэтому одинаковый набор должен лежать в config/aicompanion/music и на сервере, и у каждого игрока.
            """;
    /** A recording whose name starts with no mood number, or with a number outside the scale. */
    public static final int UNTAGGED = -1;
    /** The ends of the mood scale the owner set: 0 is the saddest recording, 999 the happiest. */
    public static final int SADDEST = 0, HAPPIEST = 999;

    /** One playable recording: its file, the sound event the client resolves for it, the title he gives it, its length and its mood.
     *  The file is the path inside the folder (a recording in a subfolder keeps that subfolder); empty for the vanilla fallback. */
    public record Track(String file, String soundId, String title, int ticks, int mood) { }

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
     * The folder read afresh for a request («другую», «повеселее», «погрустнее»): a recording put there while the game runs is
     * picked up without a reload, and a folder whose files were removed and then put back is read as it is now. The client's pack is
     * rebuilt by the request too (client/CompanionMusicPack), so the new recording is also one the sound engine knows.
     */
    public static List<Track> rescan() {
        return available();
    }

    /**
     * The standard Minecraft music for the fallback while the folder is empty (CompanionTunes): the same kind of track, with the
     * vanilla sound id the client resolves and no file of the player's. Its place on the mood scale is a calm or a cheerful one, so
     * «самую грустную» and «самую весёлую» still mean something while the folder is empty.
     */
    public static List<Track> fallback() {
        List<Track> tracks = new ArrayList<>();
        for (CompanionTunes.Tune tune : CompanionTunes.ALL)
            tracks.add(new Track("", tune.soundId(), tune.title(), tune.ticks(), tune.cheerful() ? 900 : 100));
        return List.copyOf(tracks);
    }

    /**
     * Every recording in a folder and its subfolders, by its path in the folder, with the sound id its file gets, its title, its
     * length in ticks and its mood: Ogg Vorbis files only, because the game's sound engine reads Ogg Vorbis only; other files are
     * ignored. A missing folder is simply empty, and says so once; files that are not readable are skipped with a line each. A scan
     * is asked on every tune, on every request and on every resource-pack rebuild, so a line is said once per change, not every call.
     */
    public static List<Track> scan(Path dir) {
        if (!Files.isDirectory(dir)) {
            say(dir, "the guitar folder " + dir + " is not there — the standard music sounds instead");
            return List.of();
        }
        List<Path> files;
        try (var listing = Files.walk(dir)) {
            files = listing.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing(path -> relative(dir, path), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException | java.io.UncheckedIOException error) {
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
            tracks.add(new Track(relative(dir, file), "aicompanion:guitar_folder/" + unique(clean(base), keys),
                    title(base), ticks, moodOf(base)));
        }
        // What was found, in one line: the folder is the player's own, and a scan that comes back empty is why the vanilla music plays.
        say(dir, "the guitar folder " + dir + " holds " + tracks.size() + " playable recording(s)");
        return List.copyOf(tracks);
    }

    /** A file's path inside the folder, with '/' separators: what the pack resolves against the folder. */
    private static String relative(Path dir, Path file) {
        return dir.relativize(file).toString().replace('\\', '/');
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
     * The tune for the next play: one of the kind asked for when the recordings carry that mood, otherwise any of them with
     * {@code moodMatched} false so he can say so; «vanilla» is standard Minecraft music whatever the folder holds; «next» and no
     * request take any, never the one playing. A recording of the asked kind that is playing now is played again when it is the only
     * one of that kind: an evening that goes on after a rest starts it over rather than switching to the wrong kind.
     */
    public static Pick pick(List<Track> tracks, String wanted, String avoidSoundId, RandomSource random) {
        String kind = normalize(wanted);
        if ("vanilla".equals(kind)) return new Pick(randomOf(without(fallback(), avoidSoundId), random), true);
        if (tracks.isEmpty()) return null;
        List<Track> fresh = without(tracks, avoidSoundId);
        switch (kind) {
            case "next", "" -> { return new Pick(randomOf(fresh, random), true); }
            case "saddest" -> {
                List<Track> tagged = tagged(tracks);
                if (tagged.isEmpty()) return new Pick(randomOf(fresh, random), false);
                List<Track> lowest = lowestMood(tagged);
                List<Track> other = exclude(lowest, avoidSoundId);
                return new Pick(randomOf(other.isEmpty() ? lowest : other, random), true);
            }
            case "happiest" -> {
                List<Track> tagged = tagged(tracks);
                if (tagged.isEmpty()) return new Pick(randomOf(fresh, random), false);
                List<Track> highest = highestMood(tagged);
                List<Track> other = exclude(highest, avoidSoundId);
                return new Pick(randomOf(other.isEmpty() ? highest : other, random), true);
            }
            case "sadder" -> {
                Track playing = bySoundId(tracks, avoidSoundId);
                List<Track> lower = playing == null || playing.mood() == UNTAGGED ? List.of() : below(tracks, playing.mood());
                if (!lower.isEmpty()) return new Pick(randomOf(highestMood(lower), random), true);
                List<Track> tagged = tagged(tracks);
                if (tagged.isEmpty()) return new Pick(randomOf(fresh, random), false);
                List<Track> lowest = lowestMood(tagged);
                List<Track> other = exclude(lowest, avoidSoundId);
                return new Pick(randomOf(other.isEmpty() ? lowest : other, random), true);
            }
            case "happier" -> {
                Track playing = bySoundId(tracks, avoidSoundId);
                List<Track> higher = playing == null || playing.mood() == UNTAGGED ? List.of() : above(tracks, playing.mood());
                if (!higher.isEmpty()) return new Pick(randomOf(lowestMood(higher), random), true);
                List<Track> tagged = tagged(tracks);
                if (tagged.isEmpty()) return new Pick(randomOf(fresh, random), false);
                List<Track> highest = highestMood(tagged);
                List<Track> other = exclude(highest, avoidSoundId);
                return new Pick(randomOf(other.isEmpty() ? highest : other, random), true);
            }
            default -> { return new Pick(randomOf(fresh, random), true); }
        }
    }

    /**
     * The tune for a request made while he plays: a DIFFERENT recording — the nearest lower for «погрустнее», the nearest higher
     * for «повеселее», the lowest for «самую грустную», the highest for «самую весёлую», any other for «другую», standard
     * Minecraft music for «из майнкрафта» — so the answer never promises a change that is the same recording started over. Null
     * when the folder holds no other recording at all: the one playing then simply goes on, and the answer says there are no other
     * songs. Null too when «погрустнее» or «повеселее» asks for a step the scale cannot make from the recording playing — nothing
     * strictly lower, nothing strictly higher ({@link #atEndOfScale}) — so he keeps it playing instead of jumping to the other end,
     * and the answer says plainly «Грустнее некуда.» or «Веселее некуда.». «Самую грустную» and «самую весёлую» still pick the
     * extreme itself, from wherever he is.
     */
    public static Pick change(List<Track> tracks, String wanted, String playingSoundId, RandomSource random) {
        String kind = normalize(wanted);
        if ("vanilla".equals(kind)) return new Pick(randomOf(without(fallback(), playingSoundId), random), true);
        List<Track> others = exclude(tracks, playingSoundId);
        if (others.isEmpty()) return null;
        Track playing = bySoundId(tracks, playingSoundId);
        switch (kind) {
            case "next", "" -> { return new Pick(randomOf(others, random), true); }
            case "saddest" -> {
                List<Track> tagged = tagged(others);
                return tagged.isEmpty() ? new Pick(randomOf(others, random), false)
                        : new Pick(randomOf(lowestMood(tagged), random), true);
            }
            case "happiest" -> {
                List<Track> tagged = tagged(others);
                return tagged.isEmpty() ? new Pick(randomOf(others, random), false)
                        : new Pick(randomOf(highestMood(tagged), random), true);
            }
            case "sadder" -> {
                if (playing != null && playing.mood() != UNTAGGED) {
                    List<Track> lower = below(others, playing.mood());
                    // The saddest is playing: nothing is strictly lower, and he keeps it on rather than wrap to the top.
                    if (lower.isEmpty()) return null;
                    return new Pick(randomOf(highestMood(lower), random), true);
                }
                List<Track> tagged = tagged(others);
                return tagged.isEmpty() ? new Pick(randomOf(others, random), false)
                        : new Pick(randomOf(lowestMood(tagged), random), true);
            }
            case "happier" -> {
                if (playing != null && playing.mood() != UNTAGGED) {
                    List<Track> higher = above(others, playing.mood());
                    // The happiest is playing: nothing is strictly higher, and he keeps it on rather than wrap to the bottom.
                    if (higher.isEmpty()) return null;
                    return new Pick(randomOf(lowestMood(higher), random), true);
                }
                List<Track> tagged = tagged(others);
                return tagged.isEmpty() ? new Pick(randomOf(others, random), false)
                        : new Pick(randomOf(highestMood(tagged), random), true);
            }
            default -> { return new Pick(randomOf(others, random), true); }
        }
    }

    /**
     * Whether the request is a step along the scale the folder cannot make from the recording playing: nothing strictly lower for
     * «погрустнее», nothing strictly higher for «повеселее». He keeps that recording playing then and says so plainly, instead of
     * wrapping to the opposite extreme, which was confusing: «погрустнее» on the saddest tune used to jump to the happiest.
     * A recording with no mood has no place on the scale, so the direction request is not at its end: any other recording is taken.
     */
    public static boolean atEndOfScale(List<Track> tracks, String wanted, String playingSoundId) {
        String kind = normalize(wanted);
        Track playing = bySoundId(tracks, playingSoundId);
        if (playing == null || playing.mood() == UNTAGGED) return false;
        if ("sadder".equals(kind)) return below(tracks, playing.mood()).isEmpty();
        return "happier".equals(kind) && above(tracks, playing.mood()).isEmpty();
    }

    /** The tracks other than the one playing; the whole list when that is the only one. */
    private static List<Track> without(List<Track> tracks, String soundId) {
        List<Track> fresh = exclude(tracks, soundId);
        return fresh.isEmpty() ? tracks : fresh;
    }

    /** The tracks other than the one named; empty when there is no other. */
    private static List<Track> exclude(List<Track> tracks, String soundId) {
        List<Track> fresh = new ArrayList<>();
        for (Track track : tracks) if (!track.soundId().equals(soundId)) fresh.add(track);
        return fresh;
    }

    /** The recordings that carry a mood on the scale. */
    private static List<Track> tagged(List<Track> tracks) {
        List<Track> tagged = new ArrayList<>();
        for (Track track : tracks) if (track.mood() != UNTAGGED) tagged.add(track);
        return tagged;
    }

    /** Every recording with the lowest mood in the list. */
    private static List<Track> lowestMood(List<Track> tracks) {
        int lowest = Integer.MAX_VALUE;
        for (Track track : tracks) lowest = Math.min(lowest, track.mood());
        List<Track> result = new ArrayList<>();
        for (Track track : tracks) if (track.mood() == lowest) result.add(track);
        return result;
    }

    /** Every recording with the highest mood in the list. */
    private static List<Track> highestMood(List<Track> tracks) {
        int highest = Integer.MIN_VALUE;
        for (Track track : tracks) highest = Math.max(highest, track.mood());
        List<Track> result = new ArrayList<>();
        for (Track track : tracks) if (track.mood() == highest) result.add(track);
        return result;
    }

    /** The recordings strictly lower than a mood. */
    private static List<Track> below(List<Track> tracks, int mood) {
        List<Track> result = new ArrayList<>();
        for (Track track : tracks) if (track.mood() != UNTAGGED && track.mood() < mood) result.add(track);
        return result;
    }

    /** The recordings strictly higher than a mood. */
    private static List<Track> above(List<Track> tracks, int mood) {
        List<Track> result = new ArrayList<>();
        for (Track track : tracks) if (track.mood() != UNTAGGED && track.mood() > mood) result.add(track);
        return result;
    }

    private static Track bySoundId(List<Track> tracks, String soundId) {
        for (Track track : tracks) if (track.soundId().equals(soundId)) return track;
        return null;
    }

    /** Whether the folder holds a recording other than the one playing: what tells «Таких песен нет.» from «Других песен не знаю.». */
    public static boolean hasOther(List<Track> tracks, String soundId) {
        for (Track track : tracks) if (!track.soundId().equals(soundId)) return true;
        return false;
    }

    /** Whether any recording can answer the kind asked for, so the answer to «сыграй грустное» can be honest before a tune starts. */
    public static boolean hasMood(List<Track> tracks, String wanted) {
        String kind = normalize(wanted);
        if ("vanilla".equals(kind) || kind.isEmpty() || "next".equals(kind)) return true;
        for (Track track : tracks) if (track.mood() != UNTAGGED) return true;
        return false;
    }

    /** Whether a recording is the lowest of the folder: the honest «Самая грустная» answer. */
    public static boolean isSaddest(List<Track> tracks, Track track) {
        if (track.mood() == UNTAGGED) return false;
        for (Track other : tracks) if (other.mood() != UNTAGGED && other.mood() < track.mood()) return false;
        return true;
    }

    /** Whether a recording is the highest of the folder: the honest «Самая весёлая» answer. */
    public static boolean isHappiest(List<Track> tracks, Track track) {
        if (track.mood() == UNTAGGED) return false;
        for (Track other : tracks) if (other.mood() != UNTAGGED && other.mood() > track.mood()) return false;
        return true;
    }

    /** The mood number and the spoken title, e.g. «800 Hypnotic heartbeat atmosphere»: what he says of the recording that really plays. */
    public static String label(Track track) {
        return track.mood() == UNTAGGED ? track.title() : track.mood() + " " + track.title();
    }

    /** The exact list of recordings the folder holds now, with their moods: what the model may name, and nothing else. */
    public static String recordingsLine() {
        StringBuilder line = new StringBuilder();
        for (Track track : available()) {
            if (!line.isEmpty()) line.append(", ");
            line.append(label(track));
        }
        return line.toString();
    }

    /** Whether a reply names a recording the folder does not hold: a word in Latin letters that is no part of a real title. */
    public static boolean namesUnknownRecording(String text) {
        if (text == null || text.isBlank()) return false;
        Set<String> known = new HashSet<>(Set.of("minecraft", "oleg", "ok", "f3", "t", "asmr", "id", "ogg", "mp3"));
        for (Track track : available()) {
            String title = track.title().toLowerCase(Locale.ROOT);
            known.add(title);
            for (String token : title.split("[^a-z0-9]+")) if (!token.isBlank()) known.add(token);
        }
        Matcher words = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*").matcher(text);
        while (words.find()) {
            String word = words.group().toLowerCase(Locale.ROOT);
            if (!known.contains(word)) return true;
        }
        return false;
    }

    /** The mood a request names: «calm» and «cheerful» are the old names of the ends of the scale; a plain request names none. */
    public static String normalize(String wanted) {
        if ("calm".equals(wanted)) return "saddest";
        if ("cheerful".equals(wanted)) return "happiest";
        return wanted == null ? "" : wanted;
    }

    private static Track randomOf(List<Track> tracks, RandomSource random) {
        return tracks.get(random.nextInt(tracks.size()));
    }

    /** The mood number a file name starts with, 0 (the saddest) to 999 (the happiest); {@link #UNTAGGED} when it starts with none. */
    static int moodOf(String base) {
        int digits = 0;
        while (digits < base.length() && base.charAt(digits) >= '0' && base.charAt(digits) <= '9') digits++;
        if (digits == 0) return UNTAGGED;
        try {
            int value = Integer.parseInt(base.substring(0, digits));
            return value >= SADDEST && value <= HAPPIEST ? value : UNTAGGED;
        } catch (NumberFormatException tooLong) {
            return UNTAGGED;
        }
    }

    /** The title he says: the file name without its mood number, underscores as spaces, the first letter raised. */
    static String title(String base) {
        int digits = 0;
        while (digits < base.length() && base.charAt(digits) >= '0' && base.charAt(digits) <= '9') digits++;
        String cleaned = (moodOf(base) == UNTAGGED ? base : base.substring(digits))
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
    public static int ticks(Path file) {
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

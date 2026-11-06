import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Package-boundary check over {@code src/main/java/dev/aicompanion}, by a line scanner over the code of each file.
 *
 * <p>Usage, from the repository root: {@code java scripts/ArchCheck.java}. The exceptions live in {@code tools/arch-whitelist.txt}:
 * a line {@code [client-imports]} or {@code [until-6b]} starts a section, every other non-comment line is a path relative to the
 * package directory. A whitelisted file that is missing, or no longer refers to client code, is a violation too. Exit 1 when a rule
 * is broken, 2 when the check itself cannot run.
 *
 * <p>Imports and fully qualified names are both caught. Comments (line, block, Javadoc) and string literals are skipped, but it is
 * only a line scanner: a text block ({@code """}), a {@code '"'} character literal and a name split over two lines can fool it.
 */
public final class ArchCheck {
    private static final Path SOURCES = Paths.get("src/main/java/dev/aicompanion");
    private static final Path WHITELIST = Paths.get("tools/arch-whitelist.txt");
    private static final String WHITELIST_NAME = "tools/arch-whitelist.txt";
    private static final String CLIENT_SECTION = "client-imports", SERVER_SECTION = "until-6b";
    private static final Pattern CLIENT_API = Pattern.compile("\\b(?:net\\.minecraft\\.client|com\\.mojang\\.blaze3d)\\.");
    private static final Pattern MOD_CLIENT = Pattern.compile("\\bdev\\.aicompanion\\.client\\.");
    private static final Pattern TESTS_CLASS = Pattern.compile("^(?:(?:public|final|abstract|sealed|non-sealed|strictfp)\\s+)*(?:class|record|enum|interface)\\s+(\\w+Tests)\\b");
    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/");
    private static final Set<String> SERVER_SIDE = Set.of("entity", "inventory");

    private ArchCheck() { }

    /** What a file was found to refer to, to tell a stale whitelist entry from a live one. */
    private record Hits(boolean clientApi, boolean modClient) { }

    public static void main(String[] args) {
        try {
            System.exit(run());
        } catch (IOException | RuntimeException e) {
            System.err.println("arch-check cannot run: " + e);
            System.exit(2);
        }
    }

    private static int run() throws IOException {
        if (!Files.isDirectory(SOURCES)) throw new IllegalStateException(SOURCES + " not found: run from the repository root");
        Set<String> clientFiles = new HashSet<>(), serverExceptions = new HashSet<>();
        readWhitelist(clientFiles, serverExceptions);
        List<String> violations = new ArrayList<>();
        Set<String> clientSeen = new HashSet<>(), modSeen = new HashSet<>();
        int files = 0;
        for (Path file : javaFiles()) {
            files++;
            String rel = SOURCES.relativize(file).toString().replace('\\', '/');
            Hits hits = checkFile(file, rel, clientFiles.contains(rel), serverExceptions.contains(rel), violations);
            if (hits.clientApi()) clientSeen.add(rel);
            if (hits.modClient()) modSeen.add(rel);
        }
        staleEntries(clientFiles, clientSeen, "no longer refers to net.minecraft.client / blaze3d", violations);
        staleEntries(serverExceptions, modSeen, "no longer refers to dev.aicompanion.client", violations);
        violations.forEach(System.out::println);
        System.out.println("arch-check: " + files + " files, whitelist " + clientFiles.size() + " client-import files + "
                + serverExceptions.size() + " exceptions, " + violations.size() + " violations");
        return violations.isEmpty() ? 0 : 1;
    }

    private static void staleEntries(Set<String> entries, Set<String> seen, String why, List<String> out) {
        for (String entry : entries.stream().sorted().toList()) {
            if (!seen.contains(entry)) out.add(WHITELIST_NAME + " whitelist-stale: " + entry + " is missing or " + why);
        }
    }

    private static void readWhitelist(Set<String> clientFiles, Set<String> serverExceptions) throws IOException {
        Set<String> section = null;
        for (String raw : Files.readAllLines(WHITELIST)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                String name = line.substring(1, line.length() - 1);
                section = switch (name) {
                    case CLIENT_SECTION -> clientFiles;
                    case SERVER_SECTION -> serverExceptions;
                    default -> throw new IllegalStateException("unknown whitelist section " + line);
                };
            } else if (section == null) {
                throw new IllegalStateException("whitelist entry before a section: " + line);
            } else {
                section.add(line);
            }
        }
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static Hits checkFile(Path file, String rel, boolean clientAllowed, boolean serverAllowed, List<String> out) throws IOException {
        List<String> lines = Files.readAllLines(file);
        boolean inTests = rel.startsWith("gametest/");
        boolean serverSide = !rel.contains("/") || SERVER_SIDE.contains(rel.substring(0, rel.indexOf('/')));
        boolean[] inBlockComment = {false};
        boolean clientHit = false, modHit = false, placed = false;
        for (int i = 0; i < lines.size(); i++) {
            String code = code(lines.get(i), inBlockComment);
            String at = rel + ":" + (i + 1);
            if (CLIENT_API.matcher(code).find()) {
                clientHit = true;
                if (!clientAllowed) out.add(at + " client-api: " + lines.get(i).strip() + " (add the file to " + WHITELIST_NAME + " [" + CLIENT_SECTION + "] if it is meant to)");
            }
            if (serverSide && MOD_CLIENT.matcher(code).find()) {
                modHit = true;
                if (!serverAllowed) out.add(at + " server-imports-client: " + lines.get(i).strip());
            }
            Matcher tests = TESTS_CLASS.matcher(code);
            if (!inTests && tests.find()) {
                placed = true;
                out.add(at + " tests-placement: " + tests.group(1) + " must live under gametest/");
            }
        }
        if (!inTests && !placed && rel.endsWith("Tests.java")) out.add(rel + ":1 tests-placement: a *Tests.java file must live under gametest/");
        return new Hits(clientHit, modHit);
    }

    /** The code part of a line: string literals emptied, comments cut; {@code inBlock[0]} carries an open block comment to the next line. */
    private static String code(String line, boolean[] inBlock) {
        String s = line;
        if (inBlock[0]) {
            int end = s.indexOf("*/");
            if (end < 0) return "";
            s = s.substring(end + 2);
            inBlock[0] = false;
        }
        s = STRING.matcher(s).replaceAll("\"\"");
        s = BLOCK_COMMENT.matcher(s).replaceAll(" ");
        int open = s.indexOf("/*"), slash = s.indexOf("//");
        if (slash >= 0 && (open < 0 || slash < open)) return s.substring(0, slash);
        if (open >= 0) {
            inBlock[0] = true;
            return s.substring(0, open);
        }
        return s;
    }
}

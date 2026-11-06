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
 * Package-boundary check over {@code src/main/java/dev/aicompanion}, by import lines and top-level class names.
 *
 * <p>Usage, from the repository root: {@code java scripts/ArchCheck.java}. The exceptions live in {@code tools/arch-whitelist.txt}:
 * a line {@code [client-imports]} or {@code [until-6b]} starts a section, every other non-comment line is a path relative to the
 * package directory. Exit 1 when a rule is broken, 2 when the check itself cannot run.
 */
public final class ArchCheck {
    private static final Path SOURCES = Paths.get("src/main/java/dev/aicompanion");
    private static final Path WHITELIST = Paths.get("tools/arch-whitelist.txt");
    private static final String CLIENT_SECTION = "client-imports", SERVER_SECTION = "until-6b";
    private static final Pattern CLIENT_API = Pattern.compile("^import\\s+(?:static\\s+)?(?:net\\.minecraft\\.client|com\\.mojang\\.blaze3d)\\.");
    private static final Pattern MOD_CLIENT = Pattern.compile("^import\\s+(?:static\\s+)?dev\\.aicompanion\\.client\\.");
    private static final Pattern TESTS_CLASS = Pattern.compile("^(?:(?:public|final|abstract|sealed|non-sealed|strictfp)\\s+)*(?:class|record|enum|interface)\\s+(\\w+Tests)\\b");
    private static final Set<String> SERVER_SIDE = Set.of("entity", "inventory");

    private ArchCheck() { }

    public static void main(String[] args) {
        try {
            System.exit(run());
        } catch (IOException | IllegalStateException e) {
            System.err.println("arch-check cannot run: " + e.getMessage());
            System.exit(2);
        }
    }

    private static int run() throws IOException {
        if (!Files.isDirectory(SOURCES)) throw new IllegalStateException(SOURCES + " not found: run from the repository root");
        Set<String> clientFiles = new HashSet<>(), serverExceptions = new HashSet<>();
        readWhitelist(clientFiles, serverExceptions);
        List<String> violations = new ArrayList<>();
        int files = 0;
        for (Path file : javaFiles()) {
            files++;
            String rel = SOURCES.relativize(file).toString().replace('\\', '/');
            checkFile(file, rel, clientFiles.contains(rel), serverExceptions.contains(rel), violations);
        }
        violations.forEach(System.out::println);
        System.out.println("arch-check: " + files + " files, whitelist " + clientFiles.size() + " client-import files + "
                + serverExceptions.size() + " exceptions, " + violations.size() + " violations");
        return violations.isEmpty() ? 0 : 1;
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

    private static void checkFile(Path file, String rel, boolean clientAllowed, boolean serverAllowed, List<String> out) throws IOException {
        List<String> lines = Files.readAllLines(file);
        boolean inTests = rel.startsWith("gametest/");
        boolean serverSide = !rel.contains("/") || SERVER_SIDE.contains(rel.substring(0, rel.indexOf('/')));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            String at = rel + ":" + (i + 1);
            if (!clientAllowed && CLIENT_API.matcher(line).find()) {
                out.add(at + " client-api: " + line + " (add the file to tools/arch-whitelist.txt [" + CLIENT_SECTION + "] if it is meant to)");
            }
            if (serverSide && !serverAllowed && MOD_CLIENT.matcher(line).find()) {
                out.add(at + " server-imports-client: " + line);
            }
            Matcher tests = TESTS_CLASS.matcher(lines.get(i));
            if (!inTests && tests.find()) {
                out.add(at + " tests-placement: " + tests.group(1) + " must live under gametest/");
            }
        }
    }
}

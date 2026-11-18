import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Fails when a source under {@code src/main/java} imports a type whose simple name never appears outside its own
 * import line. javac compiles such a file happily, so nothing else notices the leftover import; this check keeps them
 * from creeping back after a sweep removed them.
 *
 * <p>Usage, from the repository root: {@code java scripts/UnusedImportCheck.java}, or through
 * {@code scripts/unused-imports.sh} / {@code scripts/unused-imports.ps1}, which only locate a JDK. Exit code 0 means
 * clean, 1 means findings, 2 means the check itself could not run (the source tree is missing, nothing was scanned, an
 * internal error).
 *
 * <p>Method: purely textual, one file at a time. Every {@code import} line that names one type (not {@code import x.*})
 * is collected with its line number; the rest of the file is then searched for the simple name as a whole word. If the
 * name appears nowhere else, the import is reported as {@code path:line}. Comments, string literals and identifiers
 * of another kind are not distinguished from code, so a name that occurs only in a comment or a string counts as used:
 * the check is deliberately conservative and never asks to delete an import that is still mentioned. A name that a
 * nested class, a local variable or a field happens to share counts as used too.
 *
 * <p>Known gap: an import is judged on the file that holds it alone. A type imported for its side effect or for a
 * javadoc link keeps its name visible in the text, so it is never reported; and a name used only through a wildcard
 * import elsewhere is not confused with this file's own import.
 */
public final class UnusedImportCheck {
    private static final String ROOT = "src/main/java";

    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+)\\s*;\\s*$");

    public static void main(String[] args) {
        int code;
        try {
            code = run();
        } catch (Exception | Error failure) {
            System.err.println("unused-imports: internal error: " + failure);
            code = 2;
        }
        System.exit(code);
    }

    private static int run() throws IOException {
        Path root = Paths.get(ROOT);
        if (!Files.isDirectory(root)) {
            System.err.println("unused-imports: " + ROOT + " not found, run from the repository root");
            return 2;
        }
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(path -> path.toString().endsWith(".java"))
                    .sorted().forEach(sources::add);
        }
        if (sources.isEmpty()) {
            System.err.println("unused-imports: no Java sources under " + ROOT + ", the scan did not see the tree");
            return 2;
        }
        List<String> findings = new ArrayList<>();
        int imports = 0;
        for (Path source : sources) {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            StringBuilder body = new StringBuilder();
            List<Integer> at = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher matcher = IMPORT.matcher(line);
                if (matcher.matches() && !matcher.group(1).endsWith(".*")) {
                    at.add(i + 1);
                    names.add(matcher.group(1));
                    imports++;
                } else {
                    body.append(line).append('\n');
                }
            }
            if (names.isEmpty()) {
                continue;
            }
            String text = body.toString();
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                String simple = name.substring(name.lastIndexOf('.') + 1);
                if (!containsWord(text, simple)) {
                    findings.add(where(source) + ":" + at.get(i) + ": unused import " + name);
                }
            }
        }
        findings.forEach(System.out::println);
        System.out.println("unused-imports: " + sources.size() + " sources, " + imports + " imports, "
                + findings.size() + " unused");
        return findings.isEmpty() ? 0 : 1;
    }

    private static boolean containsWord(String text, String word) {
        Pattern pattern = Pattern.compile("(?<![\\w$])" + Pattern.quote(word) + "(?![\\w$])");
        return pattern.matcher(text).find();
    }

    private static String where(Path source) {
        return source.toString().replace('\\', '/');
    }
}

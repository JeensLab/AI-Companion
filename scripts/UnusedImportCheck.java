import java.io.IOException;
import java.nio.charset.MalformedInputException;
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
 * Fails when a source under {@code src/main/java} imports a type whose simple name never appears anywhere else in that
 * file. javac compiles such a file happily, so nothing else notices the leftover import; this check keeps them from
 * creeping back after a sweep removed them.
 *
 * <p>Usage, from the repository root: {@code java scripts/UnusedImportCheck.java}, or through
 * {@code scripts/unused-imports.sh} / {@code scripts/unused-imports.ps1}, which only locate a JDK. Exit code 0 means
 * clean, 1 means findings, 2 means the check itself could not run (the source tree is missing, nothing was scanned, an
 * internal error).
 *
 * <p>Method: purely textual, one file at a time, and deliberately conservative — it never asks to delete an import
 * that might still be needed. An import line is recognised with or without a trailing line or block comment, in LF or
 * CRLF files, and with the {@code static} modifier; {@code import x.y.*} names no single type and is counted but not
 * judged. An import-shaped line inside a block comment or a text block is not an import at all, so it is neither
 * counted nor judged: commenting an import out cannot make the check report it. For every other import the rest of the
 * file is searched for the imported simple name as a whole word: a use in code, in an annotation ({@code @Name}), in a
 * generic argument ({@code List<Name>}), in a javadoc link, in a string or in a comment all count, and so does a
 * mention inside another import line of the same file. Only an import whose name appears nowhere else at all is
 * reported, as {@code path:line: unused import fqn}. A source that is not valid UTF-8 stops the check with exit 2 and
 * a message naming the file, rather than a stack trace about a malformed input.
 *
 * <p>Known gaps: a nested class, a local variable or a field that happens to share the imported name counts as a use,
 * so the import is kept; and a multi-line import statement is left unjudged rather than guessed at.
 */
public final class UnusedImportCheck {
    private static final String ROOT = "src/main/java";

    /**
     * One import statement: {@code import a.b.C;}, {@code import static a.b.C.D;}, {@code import a.b.*;}, each with an
     * optional trailing comment.
     */
    private static final Pattern IMPORT = Pattern.compile(
            "^\\s*import\\s+(static\\s+)?([\\w.*]+)\\s*;(?:[ \\t]*(?://.*|/\\*.*?\\*/))?\\s*$");

    /** One import statement found in a file. {@code name} is null for a wildcard import, which names no single type. */
    private record ImportLine(int line, String text, String name) {
    }

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
        int wildcards = 0;
        for (Path source : sources) {
            List<String> lines;
            try {
                lines = readLines(source);
            } catch (MalformedInputException notUtf8) {
                System.err.println("unused-imports: " + where(source) + ": not valid UTF-8, cannot be scanned");
                return 2;
            }
            List<ImportLine> found = new ArrayList<>();
            StringBuilder body = new StringBuilder();
            Lexer lexer = new Lexer();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                boolean insideCommentOrTextBlock = lexer.inCommentOrTextBlock();
                lexer.scan(line);
                Matcher matcher = IMPORT.matcher(line);
                if (insideCommentOrTextBlock || !matcher.matches()) {
                    body.append(line).append('\n');
                    continue;
                }
                imports++;
                String name = matcher.group(2);
                if (name.endsWith(".*")) {
                    wildcards++;
                    found.add(new ImportLine(i + 1, line, null));
                } else {
                    found.add(new ImportLine(i + 1, line, name));
                }
            }
            String text = body.toString();
            for (ImportLine candidate : found) {
                if (candidate.name() == null) {
                    continue;
                }
                String simple = candidate.name().substring(candidate.name().lastIndexOf('.') + 1);
                if (!containsWord(text, simple) && !mentionedInAnotherImport(found, candidate, simple)) {
                    findings.add(where(source) + ":" + candidate.line() + ": unused import " + candidate.name());
                }
            }
        }
        findings.forEach(System.out::println);
        System.out.println("unused-imports: " + sources.size() + " sources, " + imports + " imports (" + wildcards
                + " wildcard), " + findings.size() + " unused");
        return findings.isEmpty() ? 0 : 1;
    }

    /**
     * True when an import line other than {@code own} mentions {@code simple} as a whole word: a nested type imported
     * through its outer class, or a static member of a class that is imported as well.
     */
    private static boolean mentionedInAnotherImport(List<ImportLine> found, ImportLine own, String simple) {
        for (ImportLine other : found) {
            if (other != own && containsWord(other.text(), simple)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tracks, line by line, whether the next line begins inside a block comment or a text block. An import can only be
     * an import at the start of a line of code; a line that begins inside {@code /* *}{@code /} or {@code """} is
     * content, so the scanner must not judge it. Line comments end with the line and need no state.
     */
    private static final class Lexer {
        private boolean blockComment;
        private boolean textBlock;

        boolean inCommentOrTextBlock() {
            return blockComment || textBlock;
        }

        /** Scans one line and leaves the state that applies to the following line. */
        void scan(String line) {
            int i = 0;
            while (i < line.length()) {
                if (textBlock) {
                    int close = closingTextBlock(line, i);
                    if (close < 0) {
                        return;
                    }
                    i = close + 3;
                    textBlock = false;
                    continue;
                }
                if (blockComment) {
                    int close = line.indexOf("*/", i);
                    if (close < 0) {
                        return;
                    }
                    i = close + 2;
                    blockComment = false;
                    continue;
                }
                char c = line.charAt(i);
                if (c == '/' && i + 1 < line.length()) {
                    char next = line.charAt(i + 1);
                    if (next == '/') {
                        return;
                    }
                    if (next == '*') {
                        blockComment = true;
                        i += 2;
                        continue;
                    }
                }
                if (c == '"') {
                    if (line.startsWith("\"\"\"", i)) {
                        textBlock = true;
                        i += 3;
                        continue;
                    }
                    i = skipQuoted(line, i, '"');
                    continue;
                }
                if (c == '\'') {
                    i = skipQuoted(line, i, '\'');
                    continue;
                }
                i++;
            }
        }

        /** Index of the {@code """} that closes a text block, or -1 when it opens onto the next line. */
        private static int closingTextBlock(String line, int from) {
            for (int i = from; i + 2 < line.length(); i++) {
                if (line.startsWith("\"\"\"", i) && (i == 0 || line.charAt(i - 1) != '\\')) {
                    return i;
                }
            }
            return -1;
        }

        /** Index just past the closing quote of a string or char literal; an unterminated literal ends the line. */
        private static int skipQuoted(String line, int start, char quote) {
            int i = start + 1;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == quote) {
                    return i + 1;
                }
                i++;
            }
            return line.length();
        }
    }

    private static List<String> readLines(Path source) throws IOException {
        String content = Files.readString(source, StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>();
        for (String line : content.split("\n", -1)) {
            lines.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
        }
        return lines;
    }

    private static boolean containsWord(String text, String word) {
        Pattern pattern = Pattern.compile("(?<![\\w$])" + Pattern.quote(word) + "(?![\\w$])");
        return pattern.matcher(text).find();
    }

    private static String where(Path source) {
        return source.toString().replace('\\', '/');
    }
}

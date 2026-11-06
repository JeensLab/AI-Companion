import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Whole-tree formatting rules over {@code src/main/java/**}{@code /*.java}; no baseline.
 *
 * <p>Usage: {@code java scripts/FormatCheck.java [--fix | --selftest]}, run from the repository root. Exit 0 clean,
 * 1 findings, 2 error. {@code --selftest} builds a throwaway tree in a temp directory and prints
 * {@code format-check selftest: N cases, ok}, or the failed cases and exit 1.
 *
 * <p>Rules: {@code trailing-whitespace} (space or tab before the line end), {@code no-newline-at-eof},
 * {@code unused-import} (single-type and static-member imports whose simple name appears nowhere outside the
 * import lines, comments and strings; a name used only in a Javadoc {@code {@link}}, {@code @see} or
 * {@code @throws} counts as used; wildcard imports are skipped). {@code \n} and {@code \r\n} are both line
 * endings: the CR is never trailing whitespace and no line ending is rewritten.
 *
 * <p>{@code --fix} repairs only the two whitespace rules and then checks again; imports are never edited.
 *
 * <p>Known gaps of {@code unused-import}: a simple name inside a string is not a use (reflection by name is not
 * seen); a class declared in the same file with the imported simple name is read as a use.
 */
public final class FormatCheck {
    private static final String SRC = "src/main/java";
    private static final String USAGE = "usage: java scripts/FormatCheck.java [--fix | --selftest]";
    private static final Pattern LEX = Pattern.compile("//[^\\n]*|/\\*[\\s\\S]*?\\*/|\"\"\"[\\s\\S]*?\"\"\""
            + "|\"(?:[^\"\\\\\\n]|\\\\.)*\"|'(?:[^'\\\\\\n]|\\\\.)*'");
    private static final Pattern IMPORT = Pattern.compile("(?m)^[ \\t]*import\\s+(?:static\\s+)?([\\w$.\\s]*?)\\s*;");
    private static final Pattern DOC_REF = Pattern.compile(
            "\\{@(?:link|linkplain|value)\\s+([^}\\n]*)|@(?:see|throws|exception)\\s+([^\\n]*)");
    private static final Pattern TRAILING = Pattern.compile("[ \\t]+(?=\\r?\\n|\\z)");

    record Finding(String file, int line, String rule, String detail) { }

    public static void main(String[] args) {
        try {
            boolean selfTest = args.length == 1 && args[0].equals("--selftest");
            boolean fix = args.length == 1 && args[0].equals("--fix");
            if (args.length > 1 || args.length == 1 && !selfTest && !fix) {
                throw new IllegalArgumentException(USAGE);
            }
            System.exit(selfTest ? selfTest() : run(Path.of("."), fix, System.out));
        } catch (Exception e) {
            System.err.println("format-check: " + e.getMessage());
            System.exit(2);
        }
    }

    private static int run(Path root, boolean fix, PrintStream out) throws IOException {
        Path src = root.resolve(SRC);
        if (!Files.isDirectory(src)) {
            throw new IllegalStateException(SRC + " not found; run from the repository root");
        }
        List<Path> files;
        try (Stream<Path> tree = Files.walk(src)) {
            files = tree.filter(p -> p.toString().endsWith(".java") && Files.isRegularFile(p)).sorted().toList();
        }
        if (fix) {
            int fixed = 0;
            for (Path file : files) {
                String before = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                String after = fix(before);
                if (!after.equals(before)) {
                    Files.write(file, after.getBytes(StandardCharsets.ISO_8859_1));
                    fixed++;
                }
            }
            out.println("format-check: fixed whitespace in " + fixed + " files");
        }
        List<Finding> findings = new ArrayList<>();
        for (Path file : files) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            findings.addAll(check(rel, new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
        }
        findings.forEach(f -> out.println(f.file + ":" + f.line + " " + f.rule + ": " + f.detail));
        out.println("format-check: " + files.size() + " files, " + findings.size() + " findings");
        return findings.isEmpty() ? 0 : 1;
    }

    static List<Finding> check(String file, String text) {
        List<Finding> out = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].endsWith("\r") ? lines[i].substring(0, lines[i].length() - 1) : lines[i];
            if (line.endsWith(" ") || line.endsWith("\t")) {
                out.add(new Finding(file, i + 1, "trailing-whitespace", "space or tab before the line end"));
            }
        }
        if (!text.isEmpty() && !text.endsWith("\n")) {
            out.add(new Finding(file, lines.length, "no-newline-at-eof", "the last line has no line ending"));
        }
        unusedImports(file, text, out);
        return out;
    }

    /** Trims trailing spaces and tabs and ends the file with a line ending; the file's own ending style is kept. */
    static String fix(String text) {
        String trimmed = TRAILING.matcher(text).replaceAll("");
        if (trimmed.isEmpty() || trimmed.endsWith("\n")) {
            return trimmed;
        }
        int lastLf = trimmed.lastIndexOf('\n');
        return trimmed + (lastLf > 0 && trimmed.charAt(lastLf - 1) == '\r' ? "\r\n" : "\n");
    }

    private static void unusedImports(String file, String text, List<Finding> out) {
        StringBuilder code = new StringBuilder();
        StringBuilder docs = new StringBuilder();
        Matcher lex = LEX.matcher(text);
        int last = 0;
        while (lex.find()) {
            String token = lex.group();
            boolean isComment = token.startsWith("//") || token.startsWith("/*");
            code.append(text, last, lex.start()).append(isComment ? " " : "\"\"")
                    .append(token.replaceAll("[^\\n]", ""));
            if (isComment) {
                docs.append(token).append('\n');
            }
            last = lex.end();
        }
        code.append(text, last, text.length());
        String body = code.toString();
        StringBuilder rest = new StringBuilder(body);
        List<String> names = new ArrayList<>();
        List<Integer> lineNumbers = new ArrayList<>();
        Matcher imports = IMPORT.matcher(body);
        while (imports.find()) {
            names.add(imports.group(1).replaceAll("\\s+", ""));
            lineNumbers.add(1 + (int) body.substring(0, imports.start()).chars().filter(c -> c == '\n').count());
            for (int i = imports.start(); i < imports.end(); i++) {
                if (rest.charAt(i) != '\n') {
                    rest.setCharAt(i, ' ');
                }
            }
        }
        StringBuilder refs = new StringBuilder();
        Matcher doc = DOC_REF.matcher(docs);
        while (doc.find()) {
            refs.append(doc.group(1) != null ? doc.group(1) : doc.group(2)).append('\n');
        }
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String simple = name.substring(name.lastIndexOf('.') + 1);
            if (simple.isEmpty() || simple.equals("*")) {
                continue;
            }
            Pattern use = Pattern.compile("(?<![\\w$.])" + Pattern.quote(simple) + "(?![\\w$])");
            if (!use.matcher(rest).find() && !use.matcher(refs).find()) {
                out.add(new Finding(file, lineNumbers.get(i), "unused-import", name));
            }
        }
    }

    private static int selfTest() throws IOException {
        Path dir = Files.createTempDirectory("format-check-selftest");
        try {
            return selfTestIn(dir);
        } finally {
            try (Stream<Path> tree = Files.walk(dir)) {
                for (Path p : tree.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
    }

    private static int selfTestIn(Path dir) throws IOException {
        List<String> failed = new ArrayList<>();
        int cases = 0;
        cases += expect(failed, "clean file", check("A.java", "class A {\n}\n"), List.of());
        cases += expect(failed, "trailing space", check("A.java", "class A {\n  int x; \n}\n"),
                List.of("A.java:2 trailing-whitespace"));
        cases += expect(failed, "trailing tab on a blank line", check("A.java", "class A {\n\t\n}\n"),
                List.of("A.java:2 trailing-whitespace"));
        cases += expect(failed, "no newline at eof", check("A.java", "class A {\n}"), List.of("A.java:2 no-newline-at-eof"));
        cases += expect(failed, "clean CRLF file", check("A.java", "import java.util.List;\r\nclass A {\r\n"
                + "  List<String> x;\r\n}\r\n"), List.of());
        cases += expect(failed, "CRLF file with trailing space", check("A.java", "class A {\r\n  int x; \r\n}\r\n"),
                List.of("A.java:2 trailing-whitespace"));
        cases += expect(failed, "unused import", check("A.java", "package p;\n\nimport java.util.Optional;\nclass A {\n}\n"),
                List.of("A.java:3 unused-import"));
        cases += expect(failed, "unused import named in a comment and a string", check("A.java",
                "import java.util.Optional;\nclass A {\n  // Optional\n  String s = \"Optional\";\n}\n"),
                List.of("A.java:1 unused-import"));
        cases += expect(failed, "import used", check("A.java", "import java.util.Optional;\nclass A {\n"
                + "  Optional<String> o;\n}\n"), List.of());
        cases += expect(failed, "wildcard import", check("A.java", "import java.util.*;\nimport static java.lang.Math.*;\n"
                + "class A {\n}\n"), List.of());
        cases += expect(failed, "only a Javadoc link", check("A.java", "import java.util.List;\nimport java.util.Set;\n"
                + "import java.util.Map;\n/** See {@link List#of()} and {@link #f(Set)}.\n * @see Map\n */\nclass A {\n}\n"),
                List.of());
        cases += expect(failed, "static import used", check("A.java", "import static java.lang.Math.max;\n"
                + "class A {\n  int m = max(1, 2);\n}\n"), List.of());
        cases += expect(failed, "static import unused", check("A.java", "import static java.lang.Math.max;\n"
                + "class A {\n  int m = other.max(1, 2);\n}\n"), List.of("A.java:1 unused-import"));
        cases += expect(failed, "fix keeps CRLF", List.of(fix("class A {\r\n  int x; \r\n}")),
                List.of("class A {\r\n  int x;\r\n}\r\n"));
        cases += expect(failed, "fix keeps LF", List.of(fix("class A {\n  int x; \n}")), List.of("class A {\n  int x;\n}\n"));
        cases += expect(failed, "fix leaves imports", List.of(fix("import java.util.Optional;\nclass A {\n}\n")),
                List.of("import java.util.Optional;\nclass A {\n}\n"));

        Path src = dir.resolve(SRC + "/p");
        Files.createDirectories(src);
        Files.writeString(src.resolve("A.java"), "class A {\n}");
        Files.writeString(src.resolve("B.java"), "class B { \r\n}\r\n");
        Files.writeString(dir.resolve(SRC + "/note.txt"), "x \n");
        cases += expect(failed, "tree run reports", runIn(dir, false), List.of(
                "src/main/java/p/A.java:2 no-newline-at-eof: the last line has no line ending",
                "src/main/java/p/B.java:1 trailing-whitespace: space or tab before the line end",
                "format-check: 2 files, 2 findings"));
        cases += expect(failed, "tree run after fix", runIn(dir, true), List.of("format-check: fixed whitespace in 2 files",
                "format-check: 2 files, 0 findings"));
        cases += expect(failed, "fix kept line endings", List.of(Files.readString(src.resolve("A.java")),
                Files.readString(src.resolve("B.java"))), List.of("class A {\n}\n", "class B {\r\n}\r\n"));
        if (failed.isEmpty()) {
            System.out.println("format-check selftest: " + cases + " cases, ok");
            return 0;
        }
        failed.forEach(System.out::println);
        System.out.println("format-check selftest: " + failed.size() + " of " + cases + " cases failed");
        return 1;
    }

    private static List<String> runIn(Path dir, boolean fix) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        run(dir, fix, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return Arrays.asList(buffer.toString(StandardCharsets.UTF_8).split("\\R"));
    }

    /** Compares findings (as {@code file:line rule}) or plain strings with the expected list; returns 1 (one case). */
    private static int expect(List<String> failed, String name, List<?> actual, List<String> expected) {
        List<String> got = new ArrayList<>();
        for (Object o : actual) {
            got.add(o instanceof Finding f ? f.file + ":" + f.line + " " + f.rule : o.toString());
        }
        if (!got.equals(expected)) {
            failed.add("FAIL " + name + ": expected " + expected + ", got " + got);
        }
        return 1;
    }
}

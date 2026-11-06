import java.io.ByteArrayOutputStream;
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
 * Ratchet over the lines a change adds to {@code src/main/java/**}{@code /*.java}: old code stays, new or touched
 * lines must pass.
 *
 * <p>Usage: {@code java scripts/LineCheck.java [--base <ref> | --selftest]}. Base defaults to the merge-base with
 * {@code origin/main}; if it is missing, exit 2 when {@code CI} is set (non-empty and not {@code false}), otherwise
 * a WARNING and {@code HEAD~1}. Checked: the diff of the working tree against the base plus untracked files.
 * Exit 0 clean, 1 findings, 2 error. {@code --selftest} runs the tool over a throwaway git repo in a temp directory
 * (never the real one) and prints {@code line-check selftest: N cases, ok}, or the failed cases and exit 1.
 *
 * <p>A changed line whose stripped text (at least 40 code points) exists anywhere in the base tree's sources is
 * moved code, not new: it is counted as moved and skipped, so a split or a relocated file does not re-flag old lines.
 *
 * <p>Rules: {@code line-length} (over 160 code points), {@code todo-comment} (TODO/FIXME/XXX in a comment),
 * {@code name} (types PascalCase; {@code static final} and interface fields UPPER_SNAKE_CASE; other fields, locals
 * and parameters camelCase, one or two lowercase letters pass). Identifiers starting with {@code _} or {@code $}
 * ({@code _y}, {@code $x}) are flagged: they are not camelCase under this rule.
 *
 * <p>Known gaps of {@code name}: one-line regexes over code with strings and comments removed, no parser. Missed:
 * several declarators in one statement ({@code int a, B;}), enum constants, {@code static}/{@code final} on another
 * line than the declaration, method names. False positive: a chain such as {@code f(a < b, c > D)} read as a
 * generic type.
 *
 * <p>Deferred: {@code JavadocMethod} for the public API of the {@code plan/} package (arrives with stage 6a).
 */
public final class LineCheck {
    private static final int MAX_LENGTH = 160;
    private static final String SRC = "src/main/java/";
    private static final String USAGE = "usage: java scripts/LineCheck.java [--base <ref> | --selftest]";
    /** A line this short matches somewhere in the base tree by coincidence, not because it moved. */
    private static final int MIN_MOVED = 40;
    private static final Pattern HUNK = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)");
    private static final Pattern TODO = Pattern.compile("\\b(TODO|FIXME|XXX)\\b");
    private static final Pattern INTERFACE_KW = Pattern.compile("(?<![\\w$])interface(?![\\w$])");
    private static final Pattern LEX = Pattern.compile("//[^\\n]*|/\\*[\\s\\S]*?\\*/|\"\"\"[\\s\\S]*?\"\"\""
            + "|\"(?:[^\"\\\\\\n]|\\\\.)*\"|'(?:[^'\\\\\\n]|\\\\.)*'");
    private static final Pattern TYPE_DECL = Pattern.compile(
            "\\b(?:class|interface|enum)\\s+([A-Za-z_$][\\w$]*)|\\brecord\\s+([A-Za-z_$][\\w$]*)\\s*[(<]");
    private static final Pattern VAR_DECL = Pattern.compile("(?<![\\w.$])([A-Za-z_][\\w.]*"
            + "(?:<[\\w\\s,.?<>\\[\\]]*>)?(?:\\[\\])*)\\s+([A-Za-z_$][\\w$]*)\\s*(?=[=;,):])");
    private static final Set<String> NOT_A_TYPE = Set.of("return", "throw", "new", "else", "case", "assert", "yield",
            "goto", "instanceof", "extends", "implements", "throws", "permits", "default", "do", "package", "import",
            "break", "continue");
    private static final Pattern PASCAL = Pattern.compile("[A-Z][A-Za-z0-9]*");
    private static final Pattern CAMEL = Pattern.compile("[a-z][A-Za-z0-9]*");
    private static final Pattern UPPER_SNAKE = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern STATIC = Pattern.compile("\\bstatic\\b");
    private static final Pattern FINAL = Pattern.compile("\\bfinal\\b");

    record Finding(String file, int line, String rule, String detail) { }

    public static void main(String[] args) {
        try {
            boolean selfTest = args.length == 1 && args[0].equals("--selftest");
            System.exit(selfTest ? selfTest() : run(Path.of("."), args, System.out));
        } catch (Exception e) {
            System.err.println("line-check: " + e.getMessage());
            System.exit(2);
        }
    }

    private static int run(Path dir, String[] args, PrintStream out) throws Exception {
        if (args.length != 0 && !(args.length == 2 && args[0].equals("--base"))) {
            throw new IllegalArgumentException(USAGE);
        }
        Path root = Path.of(git(dir, "rev-parse", "--show-toplevel").strip());
        String base = args.length == 2 ? verified(root, args[1]) : defaultBase(root);
        Map<String, Set<Integer>> changed = new TreeMap<>();
        collectDiff(git(root, "-c", "core.quotepath=false", "diff", "--no-color", "--no-ext-diff", "--src-prefix=a/",
                "--dst-prefix=b/", "-U0", "-M", base, "--", "src/main/java"), changed);
        for (String f : git(root, "-c", "core.quotepath=false", "ls-files", "-z", "--others", "--exclude-standard", "--",
                "src/main/java").split("\0")) {
            if (isSource(f)) {
                changed.put(f, null);
            }
        }
        Set<String> known = baseLines(root, base);
        List<Finding> findings = new ArrayList<>();
        int total = 0;
        int moved = 0;
        for (Map.Entry<String, Set<Integer>> entry : changed.entrySet()) {
            Path file = root.resolve(entry.getKey());
            if (Files.isRegularFile(file)) {
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\r", "");
                int[] counts = check(entry.getKey(), text, entry.getValue(), known, findings);
                total += counts[0];
                moved += counts[1];
            }
        }
        findings.forEach(f -> out.println(f.file + ":" + f.line + " " + f.rule + ": " + f.detail));
        String movedNote = moved == 0 ? "" : ", " + moved + " moved from the base tree";
        if (findings.isEmpty()) {
            out.println("line-check OK (" + total + " changed lines" + movedNote + ")");
            return 0;
        }
        out.println("line-check: " + total + " changed lines" + movedNote + ", " + findings.size() + " findings");
        return 1;
    }

    /** Every stripped line of the base tree's sources that is long enough to identify moved code. */
    private static Set<String> baseLines(Path root, String base) throws Exception {
        Set<String> lines = new HashSet<>();
        String all = tryGit(root, "-c", "core.quotepath=false", "grep", "-h", "-I", "-e", "", base, "--", SRC);
        for (String line : all == null ? new String[0] : all.split("\n")) {
            String code = line.replace("\r", "").strip();
            if (code.codePointCount(0, code.length()) >= MIN_MOVED) {
                lines.add(code);
            }
        }
        return lines;
    }

    /** Stdout of git, or null when it exits non-zero (its stderr goes through). */
    private static String tryGit(Path dir, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of("git"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return p.waitFor() == 0 ? out : null;
    }

    private static String git(Path dir, String... args) throws IOException, InterruptedException {
        String out = tryGit(dir, args);
        if (out == null) {
            throw new IllegalStateException("git failed: git " + String.join(" ", args));
        }
        return out;
    }

    private static String verified(Path root, String ref) throws Exception {
        if (tryGit(root, "rev-parse", "--verify", "--quiet", ref + "^{commit}") == null) {
            throw new IllegalArgumentException("base '" + ref + "' is not a commit");
        }
        return ref;
    }

    private static String defaultBase(Path root) throws Exception {
        String mergeBase = tryGit(root, "merge-base", "origin/main", "HEAD");
        if (mergeBase != null) {
            return mergeBase.strip();
        }
        if (isCi()) {
            throw new IllegalStateException("origin/main is missing in CI; fetch it before the check");
        }
        System.err.println("line-check: WARNING origin/main not found, comparing with HEAD~1");
        return verified(root, "HEAD~1");
    }

    private static boolean isCi() {
        String ci = System.getenv("CI");
        return ci != null && !ci.isBlank() && !ci.strip().equalsIgnoreCase("false");
    }

    private static boolean isSource(String path) {
        return path.startsWith(SRC) && path.endsWith(".java");
    }

    private static void collectDiff(String diff, Map<String, Set<Integer>> changed) {
        String file = null;
        boolean inHunk = false;
        int next = 0;
        for (String raw : diff.split("\n")) {
            String line = raw.replace("\r", "");
            if (line.startsWith("diff --git ")) {
                inHunk = false;
                file = null;
            } else if (!inHunk && line.startsWith("+++ ")) {
                String path = line.substring(4);
                path = path.endsWith("\t") ? path.substring(0, path.length() - 1) : path;
                file = path.startsWith("b/") && isSource(path.substring(2)) ? path.substring(2) : null;
            } else if (line.startsWith("@@")) {
                Matcher m = HUNK.matcher(line);
                inHunk = m.find();
                next = inHunk ? Integer.parseInt(m.group(1)) : 0;
            } else if (inHunk && file != null && line.startsWith("+")) {
                changed.computeIfAbsent(file, k -> new TreeSet<>()).add(next++);
            }
        }
    }

    /**
     * Checks the given lines (null = every line of an untracked file); returns how many lines were changed and how
     * many of them were skipped as moved from the base tree.
     */
    private static int[] check(String file, String text, Set<Integer> numbers, Set<String> known, List<Finding> out) {
        String[] raw = text.split("\n", -1);
        String[][] lexed = lex(text);
        boolean[][] interfaces = interfaceBodies(lexed[0]);
        int lines = text.endsWith("\n") ? raw.length - 1 : raw.length;
        int checked = 0;
        int moved = 0;
        for (int i = 0; i < lines; i++) {
            if (numbers != null && !numbers.contains(i + 1)) {
                continue;
            }
            checked++;
            if (known.contains(raw[i].strip())) {
                moved++;
                continue;
            }
            int length = raw[i].codePointCount(0, raw[i].length());
            if (length > MAX_LENGTH) {
                out.add(new Finding(file, i + 1, "line-length", length + " > " + MAX_LENGTH + " characters"));
            }
            Matcher todo = TODO.matcher(lexed[1][i]);
            if (todo.find()) {
                out.add(new Finding(file, i + 1, "todo-comment", todo.group(1) + " in a comment"));
            }
            checkNames(file, i + 1, lexed[0][i], interfaces[i], out);
        }
        return new int[] {checked, moved};
    }

    private static void checkNames(String file, int number, String code, boolean[] inInterface, List<Finding> out) {
        String head = code.stripLeading();
        if (head.startsWith("import ") || head.startsWith("package ")) {
            return;
        }
        Matcher t = TYPE_DECL.matcher(code);
        while (t.find()) {
            String name = t.group(1) != null ? t.group(1) : t.group(2);
            if (!PASCAL.matcher(name).matches()) {
                out.add(new Finding(file, number, "name", "type '" + name + "' is not PascalCase"));
            }
        }
        Matcher v = VAR_DECL.matcher(code);
        while (v.find()) {
            String name = v.group(2);
            String before = code.substring(0, v.start(1));
            if (NOT_A_TYPE.contains(v.group(1))) {
                continue;
            }
            boolean topLevel = before.indexOf('(') < 0 && before.indexOf('{') < 0;
            boolean constant = inInterface[v.start(1)]
                    || topLevel && STATIC.matcher(before).find() && FINAL.matcher(before).find();
            if (constant) {
                if (!UPPER_SNAKE.matcher(name).matches() && !name.equals("serialVersionUID")) {
                    out.add(new Finding(file, number, "name", "constant '" + name + "' is not UPPER_SNAKE_CASE"));
                }
            } else if (!CAMEL.matcher(name).matches()) {
                out.add(new Finding(file, number, "name", "'" + name + "' is not camelCase"));
            }
        }
    }

    /** Per code character: true when it sits directly in an {@code interface} body, outside any parentheses. */
    private static boolean[][] interfaceBodies(String[] code) {
        boolean[][] mask = new boolean[code.length][];
        Deque<Boolean> bodies = new ArrayDeque<>();
        boolean pending = false;
        int parens = 0;
        for (int i = 0; i < code.length; i++) {
            mask[i] = new boolean[code[i].length()];
            Matcher keyword = INTERFACE_KW.matcher(code[i]);
            int nextKeyword = keyword.find() ? keyword.start() : -1;
            for (int c = 0; c < code[i].length(); c++) {
                mask[i][c] = parens == 0 && Boolean.TRUE.equals(bodies.peek());
                if (c == nextKeyword) {
                    pending = true;
                    nextKeyword = keyword.find() ? keyword.start() : -1;
                }
                char ch = code[i].charAt(c);
                if (ch == '{') {
                    bodies.push(pending);
                    pending = false;
                } else if (ch == '}' && !bodies.isEmpty()) {
                    bodies.pop();
                } else if (ch == '(') {
                    parens++;
                } else if (ch == ')' && parens > 0) {
                    parens--;
                }
            }
        }
        return mask;
    }

    /** Line-aligned views of the text: [0] code with strings emptied and comments removed, [1] comment text. */
    private static String[][] lex(String text) {
        StringBuilder code = new StringBuilder();
        StringBuilder comments = new StringBuilder();
        Matcher m = LEX.matcher(text);
        int last = 0;
        while (m.find()) {
            String plain = text.substring(last, m.start());
            String token = m.group();
            String newlines = token.replaceAll("[^\\n]", "");
            boolean isComment = token.startsWith("/") && (token.charAt(1) == '/' || token.charAt(1) == '*');
            code.append(plain).append(isComment ? " " : token.startsWith("'") ? "' '" : "\"\"").append(newlines);
            comments.append(plain.replaceAll("[^\\n]", "")).append(isComment ? token : newlines);
            last = m.end();
        }
        String tail = text.substring(last);
        code.append(tail);
        comments.append(tail.replaceAll("[^\\n]", ""));
        return new String[][] {code.toString().split("\n", -1), comments.toString().split("\n", -1)};
    }

    private static int selfTest() throws Exception {
        Path dir = Files.createTempDirectory("line-check-selftest");
        try {
            return selfTestIn(dir);
        } finally {
            try (Stream<Path> tree = Files.walk(dir)) {
                for (Path p : tree.sorted(Comparator.reverseOrder()).toList()) {
                    p.toFile().setWritable(true);
                    Files.delete(p);
                }
            }
        }
    }

    private static int selfTestIn(Path dir) throws Exception {
        String a = "src/main/java/p/A.java";
        String spaced = "src/main/java/p q/E F.java";
        git(dir, "init", "-q", "-b", "main");
        for (String kv : new String[] {"user.name=selftest", "user.email=selftest@example.invalid",
                "commit.gpgsign=false", "core.autocrlf=false"}) {
            git(dir, "config", kv.substring(0, kv.indexOf('=')), kv.substring(kv.indexOf('=') + 1));
        }
        String longLine = "    String s = \"" + "x".repeat(170) + "\";\n";
        put(dir, a, "class A {\n}\n");
        put(dir, spaced, "class E {\n}\n");
        put(dir, "src/main/java/p/L.java", "class L {\n" + longLine + "}\n");
        git(dir, "add", "--", "src");
        git(dir, "commit", "-q", "-m", "baseline");
        String sha = git(dir, "rev-parse", "HEAD").strip();
        List<String> failed = new ArrayList<>();
        int cases = 0;
        for (String option : new String[] {"diff.noprefix", "diff.mnemonicPrefix"}) {
            git(dir, "config", option, "true");
            put(dir, a, "class A {\n    int Bad = 1;\n}\n");
            cases += expect(failed, option, probe(dir, sha), "A.java:2 name: 'Bad' is not camelCase");
            git(dir, "config", "--unset", option);
        }
        put(dir, a, "class A {\n}\n");
        put(dir, spaced, "class E {\n    int Bad;\n}\n");
        cases += expect(failed, "path with a space", probe(dir, sha), "E F.java:2 name: 'Bad' is not camelCase");
        put(dir, spaced, "class E {\n}\n");
        String unicode = "src/main/java/p/Привет.java";
        put(dir, unicode, "class Hello {\n    int Bad;\n}\n");
        cases += expect(failed, "untracked non-ASCII name", probe(dir, sha), "вет.java:2 name: 'Bad'");
        Files.delete(dir.resolve(unicode));
        put(dir, a, "interface I {\n    int MAX = 2;\n}\ninterface J { int MAX = 2; }\nclass B {\n    void f() {\n"
                + "        OUTERX:\n        for (;;) {\n            break OUTERX;\n        }\n"
                + "        OUTERY:\n        for (;;) {\n            continue OUTERY;\n        }\n    }\n}\n");
        cases += expect(failed, "interface constant and labels", probe(dir, sha), null);
        put(dir, a, "interface K {\n    int bad = 2;\n}\n");
        cases += expect(failed, "interface field in lower case", probe(dir, sha), "constant 'bad' is not UPPER_SNAKE_CASE");
        put(dir, a, "class A {\n" + longLine + "}\n");
        cases += expect(failed, "long line moved from the base tree", probe(dir, sha), null);
        put(dir, a, "class A {\n" + longLine.replace('x', 'y') + "}\n");
        cases += expect(failed, "new long line", probe(dir, sha), "A.java:2 line-length: 188 > 160");
        put(dir, a, "class A {\n    int ok = 1;\n}\n");
        cases += expect(failed, "negative control", probe(dir, sha), null);
        if (failed.isEmpty()) {
            System.out.println("line-check selftest: " + cases + " cases, ok");
            return 0;
        }
        failed.forEach(f -> System.out.println("line-check selftest FAILED: " + f));
        return 1;
    }

    /** Exit code, a bar and the output of a normal run with the baseline commit as base. */
    private static String probe(Path dir, String sha) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int code = run(dir, new String[] {"--base", sha}, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return code + "|" + buffer.toString(StandardCharsets.UTF_8);
    }

    /** A null needle expects a clean run, otherwise exit 1 and the needle in the output; returns 1 as the case count. */
    private static int expect(List<String> failed, String name, String result, String needle) {
        boolean ok = needle == null ? result.startsWith("0|line-check OK") : result.startsWith("1|") && result.contains(needle);
        if (!ok) {
            failed.add(name + ": got " + result.strip().replace("\n", " / "));
        }
        return 1;
    }

    private static void put(Path dir, String relative, String text) throws IOException {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}

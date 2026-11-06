import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ratchet over the lines a change adds to {@code src/main/java/**}{@code /*.java}: old code stays, new or touched
 * lines must pass.
 *
 * <p>Usage: {@code java scripts/LineCheck.java [--base <ref>]}. Base defaults to the merge-base with
 * {@code origin/main}; if it is missing, exit 2 when {@code CI} is set, otherwise a WARNING and {@code HEAD~1}.
 * Checked: the diff of the working tree against the base plus untracked files. Exit 0 clean, 1 findings, 2 error.
 *
 * <p>Rules: {@code line-length} (over 160 code points), {@code todo-comment} (TODO/FIXME/XXX in a comment),
 * {@code name} (types PascalCase; {@code static final} UPPER_SNAKE_CASE; other fields, locals and parameters
 * camelCase, one or two lowercase letters pass).
 *
 * <p>Known gaps of {@code name}: one-line regexes over code with strings and comments removed, no parser. Missed:
 * several declarators in one statement ({@code int a, B;}), implicit interface constants, enum constants,
 * {@code static}/{@code final} on another line than the declaration, method names. False positive: a chain such
 * as {@code f(a < b, c > D)} read as a generic type.
 *
 * <p>Deferred: {@code JavadocMethod} for the public API of the {@code plan/} package (arrives with stage 6a).
 */
public final class LineCheck {
    private static final int MAX_LENGTH = 160;
    private static final String SRC = "src/main/java/";
    private static final Pattern HUNK = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)");
    private static final Pattern TODO = Pattern.compile("\\b(TODO|FIXME|XXX)\\b");
    private static final Pattern LEX = Pattern.compile("//[^\\n]*|/\\*[\\s\\S]*?\\*/|\"\"\"[\\s\\S]*?\"\"\""
            + "|\"(?:[^\"\\\\\\n]|\\\\.)*\"|'(?:[^'\\\\\\n]|\\\\.)*'");
    private static final Pattern TYPE_DECL = Pattern.compile(
            "\\b(?:class|interface|enum)\\s+([A-Za-z_$][\\w$]*)|\\brecord\\s+([A-Za-z_$][\\w$]*)\\s*[(<]");
    private static final Pattern VAR_DECL = Pattern.compile("(?<![\\w.$])([A-Za-z_][\\w.]*"
            + "(?:<[\\w\\s,.?<>\\[\\]]*>)?(?:\\[\\])*)\\s+([A-Za-z_$][\\w$]*)\\s*(?=[=;,):])");
    private static final Set<String> NOT_A_TYPE = Set.of("return", "throw", "new", "else", "case", "assert", "yield",
            "goto", "instanceof", "extends", "implements", "throws", "permits", "default", "do", "package", "import");
    private static final Pattern PASCAL = Pattern.compile("[A-Z][A-Za-z0-9]*");
    private static final Pattern CAMEL = Pattern.compile("[a-z][A-Za-z0-9]*");
    private static final Pattern UPPER_SNAKE = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern STATIC = Pattern.compile("\\bstatic\\b");
    private static final Pattern FINAL = Pattern.compile("\\bfinal\\b");

    record Finding(String file, int line, String rule, String detail) { }

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (Exception e) {
            System.err.println("line-check: " + e.getMessage());
            System.exit(2);
        }
    }

    private static int run(String[] args) throws Exception {
        if (args.length != 0 && !(args.length == 2 && args[0].equals("--base"))) {
            throw new IllegalArgumentException("usage: java scripts/LineCheck.java [--base <ref>]");
        }
        Path root = Path.of(Objects.requireNonNull(git(Path.of("."), "rev-parse", "--show-toplevel")).strip());
        String base = args.length == 2 ? verified(root, args[1]) : defaultBase(root);
        Map<String, Set<Integer>> changed = new TreeMap<>();
        collectDiff(Objects.requireNonNull(git(root, "-c", "core.quotepath=false", "diff", "--no-color", "--no-ext-diff", "-U0", "-M",
                base, "--", "src/main/java")), changed);
        for (String f : Objects.requireNonNull(git(root, "ls-files", "--others", "--exclude-standard", "--", "src/main/java")).split("\n")) {
            if (isSource(f.strip())) {
                changed.put(f.strip(), null);
            }
        }
        List<Finding> findings = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, Set<Integer>> entry : changed.entrySet()) {
            Path file = root.resolve(entry.getKey());
            if (Files.isRegularFile(file)) {
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\r", "");
                total += check(entry.getKey(), text, entry.getValue(), findings);
            }
        }
        findings.forEach(f -> System.out.println(f.file + ":" + f.line + " " + f.rule + ": " + f.detail));
        if (findings.isEmpty()) {
            System.out.println("line-check OK (" + total + " changed lines)");
            return 0;
        }
        System.out.println("line-check: " + total + " changed lines, " + findings.size() + " findings");
        return 1;
    }

    /** Stdout of git, or null when it exits non-zero (its stderr goes through). */
    private static String git(Path dir, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of("git"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return p.waitFor() == 0 ? out : null;
    }

    private static String verified(Path root, String ref) throws Exception {
        if (git(root, "rev-parse", "--verify", "--quiet", ref + "^{commit}") == null) {
            throw new IllegalArgumentException("base '" + ref + "' is not a commit");
        }
        return ref;
    }

    private static String defaultBase(Path root) throws Exception {
        String mergeBase = git(root, "merge-base", "origin/main", "HEAD");
        if (mergeBase != null) {
            return mergeBase.strip();
        }
        if (System.getenv("CI") != null) {
            throw new IllegalStateException("origin/main is missing in CI; fetch it before the check");
        }
        System.err.println("line-check: WARNING origin/main not found, comparing with HEAD~1");
        return verified(root, "HEAD~1");
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

    /** Checks the given lines (null = every line of an untracked file); returns how many lines were checked. */
    private static int check(String file, String text, Set<Integer> numbers, List<Finding> out) {
        String[] raw = text.split("\n", -1);
        String[][] lexed = lex(text);
        int lines = text.endsWith("\n") ? raw.length - 1 : raw.length;
        int checked = 0;
        for (int i = 0; i < lines; i++) {
            if (numbers != null && !numbers.contains(i + 1)) {
                continue;
            }
            checked++;
            int length = raw[i].codePointCount(0, raw[i].length());
            if (length > MAX_LENGTH) {
                out.add(new Finding(file, i + 1, "line-length", length + " > " + MAX_LENGTH + " characters"));
            }
            Matcher todo = TODO.matcher(lexed[1][i]);
            if (todo.find()) {
                out.add(new Finding(file, i + 1, "todo-comment", todo.group(1) + " in a comment"));
            }
            checkNames(file, i + 1, lexed[0][i], out);
        }
        return checked;
    }

    private static void checkNames(String file, int number, String code, List<Finding> out) {
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
            if (topLevel && STATIC.matcher(before).find() && FINAL.matcher(before).find()) {
                if (!UPPER_SNAKE.matcher(name).matches() && !name.equals("serialVersionUID")) {
                    out.add(new Finding(file, number, "name", "constant '" + name + "' is not UPPER_SNAKE_CASE"));
                }
            } else if (!CAMEL.matcher(name).matches()) {
                out.add(new Finding(file, number, "name", "'" + name + "' is not camelCase"));
            }
        }
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
}

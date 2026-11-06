import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ratchet for magic numbers: reads {@code git diff -U0} from stdin and checks only the ADDED lines of production Java
 * ({@code src/main/java/dev/aicompanion/**}, without {@code gametest/} and without {@code *Tests.java}). The old code is
 * never judged, so the rule can tighten without a whole-tree baseline.
 *
 * <p>A numeric literal on an added line passes when one of these holds:
 * <ul>
 *   <li>its magnitude is in {@link #ALLOWED_LITERALS};</li>
 *   <li>the line declares a constant ({@code static final ... =}) - the name is the explanation; only that line is
 *       exempt, continuation lines and enum constant arguments need a comment;</li>
 *   <li>the line carries a comment that states a reason - the presence of the comment is the mark that the reason is
 *       written next to the number; an empty comment or a bare TODO/FIXME/XXX does not count;</li>
 *   <li>the line is nothing but an annotation ({@code @Name} or {@code @Name(...)}).</li>
 * </ul>
 * Text inside comments, string literals and char literals is ignored; whether a line starts inside a block comment is
 * read from the working-tree file. Everything else is reported as {@code file:line literal context}.
 *
 * <p>Usage, from the repository root (the wrappers {@code scripts/magic-numbers.sh|ps1} do this):
 * <pre>
 *   git diff -U0 origin/main -- src/main/java | java scripts/MagicNumbers.java
 * </pre>
 * Exit code: 0 - clean, 1 - literals need a name or a comment.
 */
public final class MagicNumbers {
    /**
     * Literals that need no name. The sign is not part of a literal ({@code -1} is the unary minus applied to {@code 1}),
     * so the comparison uses the magnitude: {@code -1}, {@code -0.5} and {@code 2} all pass.
     */
    private static final List<BigDecimal> ALLOWED_LITERALS = List.of(
            new BigDecimal("0"), new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("-1"),
            new BigDecimal("0.5"), new BigDecimal("1.0"), new BigDecimal("0.0"));

    private static final String SOURCE_ROOT = "src/main/java/dev/aicompanion/";
    private static final String EXCLUDED_DIR = "/gametest/";
    private static final String EXCLUDED_SUFFIX = "Tests.java";
    private static final Pattern ANNOTATION_ONLY = Pattern.compile("@\\w+(?:\\.\\w+)*(?:\\s*\\([^)]*\\))?");
    private static final Pattern PLACEHOLDER_COMMENT = Pattern.compile("(?i)\\s*(?:todo|fixme|xxx)\\s*:?\\s*");
    private static final Pattern HUNK = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");
    private static final Pattern CONSTANT_DECLARATION = Pattern.compile("\\b(?:static\\s+final|final\\s+static)\\b[^=]*=");
    private static final Pattern LITERAL = Pattern.compile(
            "(?<![\\w.$])(?:0[xX][0-9a-fA-F_]+[lL]?|0[bB][01_]+[lL]?"
                    + "|(?:\\d[\\d_]*(?:\\.[\\d_]*)?|\\.\\d[\\d_]*)(?:[eE][+-]?\\d[\\d_]*)?[fFdDlL]?)(?![\\w$])");

    private record Stripped(String code, boolean hasComment, boolean endsInBlock) {
    }

    private record Result(int changedLines, int hits) {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        Result result = scan(in, out);
        if (result.hits() > 0) {
            out.println("magic-numbers: " + result.hits() + " literal(s) in changed lines need a name or a comment");
            System.exit(1);
        }
        out.println("magic-numbers OK (" + result.changedLines() + " changed production lines)");
    }

    private static Result scan(BufferedReader in, PrintStream out) throws Exception {
        Map<String, boolean[]> commentStarts = new HashMap<>();
        String file = null;
        boolean inHunk = false;
        int lineNo = 0;
        int changed = 0;
        int hits = 0;
        String line;
        while ((line = in.readLine()) != null) {
            Matcher hunk = HUNK.matcher(line);
            if (line.startsWith("diff ")) {
                inHunk = false;
            } else if (hunk.find()) {
                inHunk = true;
                lineNo = Integer.parseInt(hunk.group(1));
            } else if (!inHunk && line.startsWith("+++ ")) {
                file = productionPath(line.substring(4));
            } else if (inHunk && line.startsWith("+")) {
                if (file != null) {
                    changed++;
                    hits += report(file, lineNo, line.substring(1), out, commentStarts);
                }
                lineNo++;
            }
        }
        return new Result(changed, hits);
    }

    /** Returns the repository path when the diff header names a production Java file, otherwise null. */
    private static String productionPath(String header) {
        String path = header.split("\t")[0];
        if (path.startsWith("b/")) {
            path = path.substring(2);
        }
        boolean production = path.contains(SOURCE_ROOT) && path.endsWith(".java") && !path.endsWith(EXCLUDED_SUFFIX)
                && !path.contains(EXCLUDED_DIR);
        return production ? path : null;
    }

    private static int report(String file, int lineNo, String line, PrintStream out, Map<String, boolean[]> commentStarts) {
        boolean inBlock = startsInsideBlockComment(file, lineNo, commentStarts);
        List<String> literals = unnamedLiterals(line, inBlock);
        for (String literal : literals) {
            out.println(file + ":" + lineNo + " " + literal + " " + line.trim());
        }
        return literals.size();
    }

    private static List<String> unnamedLiterals(String line, boolean startsInsideBlock) {
        Stripped stripped = strip(line, startsInsideBlock);
        String code = stripped.code().trim();
        if (stripped.hasComment() || ANNOTATION_ONLY.matcher(code).matches()
                || CONSTANT_DECLARATION.matcher(code).find()) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        Matcher matcher = LITERAL.matcher(code);
        while (matcher.find()) {
            if (!isAllowed(matcher.group())) {
                found.add(matcher.group());
            }
        }
        return found;
    }

    private static boolean isAllowed(String token) {
        BigDecimal value = valueOf(token);
        return ALLOWED_LITERALS.stream().anyMatch(allowed -> allowed.abs().compareTo(value) == 0);
    }

    private static BigDecimal valueOf(String token) {
        String text = token.replace("_", "");
        if (text.startsWith("0x") || text.startsWith("0X")) {
            return new BigDecimal(new BigInteger(text.substring(2).replaceAll("[lL]$", ""), 16));
        }
        if (text.startsWith("0b") || text.startsWith("0B")) {
            return new BigDecimal(new BigInteger(text.substring(2).replaceAll("[lL]$", ""), 2));
        }
        return new BigDecimal(text.replaceAll("[fFdDlL]$", ""));
    }

    /**
     * Whether the line begins inside a {@code /* *}{@code /} comment. A diff with zero context cannot tell, so the
     * answer comes from the working-tree file (the diff is taken against the working tree), scanned once per file.
     */
    private static boolean startsInsideBlockComment(String file, int lineNo, Map<String, boolean[]> cache) {
        boolean[] starts = cache.computeIfAbsent(file, MagicNumbers::blockCommentStarts);
        return lineNo >= 1 && lineNo <= starts.length && starts[lineNo - 1];
    }

    private static boolean[] blockCommentStarts(String file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(file), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return new boolean[0];
        }
        boolean[] starts = new boolean[lines.size()];
        boolean inBlock = false;
        for (int i = 0; i < starts.length; i++) {
            starts[i] = inBlock;
            inBlock = strip(lines.get(i), inBlock).endsInBlock();
        }
        return starts;
    }

    /**
     * Blanks out string and char literals and comments; remembers whether a comment with a stated reason was present
     * and whether the line ends inside a block comment.
     */
    private static Stripped strip(String line, boolean startsInsideBlock) {
        StringBuilder code = new StringBuilder();
        boolean comment = false;
        boolean inBlock = startsInsideBlock;
        int i = 0;
        while (i < line.length()) {
            if (inBlock) {
                int end = line.indexOf("*/", i);
                int bodyEnd = end < 0 ? line.length() : end;
                comment |= isReason(line.substring(i, bodyEnd));
                inBlock = end < 0;
                i = end < 0 ? line.length() : end + 2;
                code.append(' ');
                continue;
            }
            char c = line.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipQuoted(line, i);
                code.append(' ');
            } else if (line.startsWith("//", i)) {
                comment |= isReason(line.substring(i + 2));
                break;
            } else if (line.startsWith("/*", i)) {
                inBlock = true;
                i += 2;
            } else {
                code.append(c);
                i++;
            }
        }
        return new Stripped(code.toString(), comment, inBlock);
    }

    /** A comment is a reason when it says something; an empty one or a bare TODO/FIXME/XXX marker is not. */
    private static boolean isReason(String commentText) {
        return !commentText.isBlank() && !PLACEHOLDER_COMMENT.matcher(commentText).matches();
    }

    private static int skipQuoted(String line, int start) {
        char quote = line.charAt(start);
        int i = start + 1;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else {
                i++;
            }
        }
        return line.length();
    }
}

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ratchet for magic numbers: reads {@code git diff -U0} from stdin and checks only the ADDED lines of production Java
 * ({@code src/main/java/dev/aicompanion/**}, without {@code gametest/}). The old code is never judged, so the rule can
 * tighten without a whole-tree baseline.
 *
 * <p>A numeric literal on an added line passes when one of these holds:
 * <ul>
 *   <li>its magnitude is in {@link #ALLOWED_LITERALS};</li>
 *   <li>the line declares a constant ({@code static final ... =}) - the name is the explanation;</li>
 *   <li>the line carries a non-empty trailing or inline comment - the presence of the comment is the mark that the
 *       reason is written next to the number;</li>
 *   <li>the line is an annotation (starts with {@code @}) or a Javadoc continuation (starts with {@code *}).</li>
 * </ul>
 * Literals inside string and char literals are ignored. Everything else is reported as {@code file:line literal context}.
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
    private static final Pattern HUNK = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");
    private static final Pattern CONSTANT_DECLARATION = Pattern.compile("\\b(?:static\\s+final|final\\s+static)\\b[^=]*=");
    private static final Pattern LITERAL = Pattern.compile(
            "(?<![\\w.$])(?:0[xX][0-9a-fA-F_]+[lL]?|0[bB][01_]+[lL]?"
                    + "|(?:\\d[\\d_]*(?:\\.[\\d_]*)?|\\.\\d[\\d_]*)(?:[eE][+-]?\\d[\\d_]*)?[fFdDlL]?)(?![\\w$])");

    private record Stripped(String code, boolean hasComment) {
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
                    hits += report(file, lineNo, line.substring(1), out);
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
        boolean production = path.contains(SOURCE_ROOT) && path.endsWith(".java") && !path.contains(EXCLUDED_DIR);
        return production ? path : null;
    }

    private static int report(String file, int lineNo, String line, PrintStream out) {
        List<String> literals = unnamedLiterals(line);
        for (String literal : literals) {
            out.println(file + ":" + lineNo + " " + literal + " " + line.trim());
        }
        return literals.size();
    }

    private static List<String> unnamedLiterals(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("@") || trimmed.startsWith("*")) {
            return List.of();
        }
        Stripped stripped = strip(line);
        if (stripped.hasComment() || CONSTANT_DECLARATION.matcher(stripped.code()).find()) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        Matcher matcher = LITERAL.matcher(stripped.code());
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

    /** Blanks out string and char literals and comments; remembers whether a non-empty comment was present. */
    private static Stripped strip(String line) {
        StringBuilder code = new StringBuilder();
        boolean comment = false;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipQuoted(line, i);
                code.append(' ');
            } else if (line.startsWith("//", i)) {
                comment |= !line.substring(i + 2).isBlank();
                break;
            } else if (line.startsWith("/*", i)) {
                int end = line.indexOf("*/", i + 2);
                int bodyEnd = end < 0 ? line.length() : end;
                comment |= !line.substring(i + 2, bodyEnd).isBlank();
                if (end < 0) {
                    break;
                }
                i = end + 2;
                code.append(' ');
            } else {
                code.append(c);
                i++;
            }
        }
        return new Stripped(code.toString(), comment);
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

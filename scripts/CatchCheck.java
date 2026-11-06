import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Parse-only check of the error-handling rules for production catch blocks.
 * Usage: java scripts/CatchCheck.java [repoRoot]. Exit 0 clean, 1 findings, 2 tool error.
 *
 * Rules: empty-catch, generic-catch, tick-swallows-error, baseline-stale.
 * A catch is suppressed by tools/catch-baseline.txt, one "file:method:ordinal reason" per line
 * (ordinal = 1-based position of the catch among the catches of that method name in the file).
 * The key has no line number, so unrelated edits above the catch do not invalidate it.
 */
public class CatchCheck {
    static final String SOURCE_DIR = "src/main/java/dev/aicompanion";
    static final String BASELINE = "tools/catch-baseline.txt";
    static final Set<String> GENERIC = Set.of("Exception", "RuntimeException", "Throwable", "Error");
    static final Set<String> FATAL = Set.of("Throwable", "Error");
    static final Set<String> LOGGERS = Set.of("LOGGER", "LOG", "log");

    record Finding(String file, long line, String rule, String detail, String key) {}

    static final class ToolError extends RuntimeException {
        ToolError(String message) { super(message); }
    }

    public static void main(String[] args) {
        try {
            System.exit(run(Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize()));
        } catch (ToolError | IOException error) {
            System.err.println("catch-check: " + error.getMessage());
            System.exit(2);
        }
    }

    static int run(Path root) throws IOException {
        Map<String, String> baseline = readBaseline(root.resolve(BASELINE));
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root.resolve(SOURCE_DIR))) {
            files = walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/gametest/"))
                    .filter(p -> !p.getFileName().toString().endsWith("Tests.java"))
                    .sorted().toList();
        } catch (NoSuchFileException missing) {
            throw new ToolError("no such directory: " + missing.getFile());
        }
        if (files.isEmpty()) throw new ToolError("no source files under " + SOURCE_DIR);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new ToolError("a JDK is required (no system Java compiler)");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<Finding> all = new ArrayList<>();
        int[] catches = {0};
        try (var fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, diagnostics,
                    List.of("-proc:none"), null, fileManager.getJavaFileObjectsFromPaths(files));
            Iterable<? extends CompilationUnitTree> units = task.parse();
            List<String> errors = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(d -> relative(root, Path.of(d.getSource().toUri())) + ":" + d.getLineNumber()
                            + ": " + d.getMessage(null))
                    .toList();
            if (!errors.isEmpty()) {
                errors.forEach(System.err::println);
                throw new ToolError("parse errors: " + errors.size());
            }
            SourcePositions positions = Trees.instance(task).getSourcePositions();
            for (CompilationUnitTree unit : units) {
                String file = relative(root, Path.of(unit.getSourceFile().toUri()));
                new CatchScanner(unit, positions, file, all, catches).scan(unit, null);
            }
        }

        Set<String> flagged = all.stream().map(Finding::key).collect(Collectors.toSet());
        List<Finding> findings = all.stream().filter(f -> !baseline.containsKey(f.key())).collect(Collectors.toList());
        baseline.keySet().stream().filter(k -> !flagged.contains(k)).sorted().forEach(k ->
                findings.add(new Finding(BASELINE, 0, "baseline-stale",
                        "no flagged catch for " + k + "; remove the entry", k)));
        findings.sort(Comparator.comparing(Finding::file).thenComparingLong(Finding::line).thenComparing(Finding::rule));
        for (Finding f : findings) {
            System.out.println(f.file() + ":" + f.line() + " " + f.rule() + ": " + f.detail());
        }
        System.out.println("catch-check: " + catches[0] + " catches, " + findings.size() + " findings");
        return findings.isEmpty() ? 0 : 1;
    }

    static String relative(Path root, Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    static Map<String, String> readBaseline(Path path) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        if (!Files.exists(path)) return entries;
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+", 2);
            if (parts.length < 2 || parts[0].split(":").length != 3) {
                throw new ToolError(BASELINE + ":" + (i + 1) + ": expected 'file:method:ordinal reason'");
            }
            if (entries.put(parts[0], parts[1]) != null) {
                throw new ToolError(BASELINE + ":" + (i + 1) + ": duplicate entry " + parts[0]);
            }
        }
        return entries;
    }

    static final class CatchScanner extends TreeScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final String file;
        private final List<Finding> out;
        private final int[] total;
        private final Deque<String> methods = new ArrayDeque<>();
        private final Map<String, Integer> ordinals = new HashMap<>();

        CatchScanner(CompilationUnitTree unit, SourcePositions positions, String file, List<Finding> out, int[] total) {
            this.unit = unit;
            this.positions = positions;
            this.file = file;
            this.out = out;
            this.total = total;
        }

        @Override
        public Void visitMethod(MethodTree method, Void unused) {
            methods.push(method.getName().toString());
            try {
                return super.visitMethod(method, unused);
            } finally {
                methods.pop();
            }
        }

        @Override
        public Void visitCatch(CatchTree node, Void unused) {
            total[0]++;
            String method = methods.isEmpty() ? "<init>" : methods.peek();
            String key = file + ":" + method + ":" + ordinals.merge(method, 1, Integer::sum);
            long line = unit.getLineMap().getLineNumber(positions.getStartPosition(unit, node));
            List<String> types = caughtTypes(node.getParameter().getType());
            String where = "catch (" + String.join(" | ", types) + ") ";
            if (node.getBlock().getStatements().isEmpty()) {
                out.add(new Finding(file, line, "empty-catch", where + "has an empty body [" + key + "]", key));
            }
            if (types.stream().anyMatch(GENERIC::contains) && !logsOrThrows(node.getBlock())) {
                out.add(new Finding(file, line, "generic-catch", where + "neither logs nor throws [" + key + "]", key));
            }
            Optional<String> tick = methods.stream().filter(m -> m.startsWith("tick")).findFirst();
            if (tick.isPresent() && types.stream().anyMatch(FATAL::contains)) {
                out.add(new Finding(file, line, "tick-swallows-error", where + "inside " + tick.get() + " [" + key + "]", key));
            }
            return super.visitCatch(node, unused);
        }

        private static List<String> caughtTypes(Tree type) {
            List<? extends Tree> alternatives = type instanceof UnionTypeTree union ? union.getTypeAlternatives() : List.of(type);
            return alternatives.stream().map(t -> t.toString().replaceAll("^.*\\.", "")).toList();
        }

        private static boolean logsOrThrows(BlockTree block) {
            boolean[] found = {false};
            new TreeScanner<Void, Void>() {
                @Override
                public Void visitThrow(ThrowTree node, Void unused) {
                    found[0] = true;
                    return null;
                }

                @Override
                public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                    if (node.getMethodSelect() instanceof MemberSelectTree select && isLogger(select.getExpression())) {
                        found[0] = true;
                    }
                    return super.visitMethodInvocation(node, unused);
                }
            }.scan(block, null);
            return found[0];
        }

        private static boolean isLogger(ExpressionTree receiver) {
            if (receiver instanceof IdentifierTree id) return LOGGERS.contains(id.getName().toString());
            return receiver instanceof MemberSelectTree select && select.getIdentifier().contentEquals("LOGGER");
        }
    }
}

import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.lang.model.element.Modifier;
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
 * Only a LOGGER/LOG/log call or a throw counts as handling a generic catch; only a throw
 * silences tick-swallows-error (logging an Error in tick still swallows it).
 * tools/catch-baseline.txt suppresses a catch per rule, one "key rules reason" per line, where
 * key = file:Outer.Inner:method/arity:ordinal (initializers are <clinit>/<init>, an anonymous class is
 * <anon@enclosingMethod/arity>) and ordinal is the 1-based position among the catches of that scope.
 * The key has no line number, so unrelated edits do not invalidate it.
 */
public class CatchCheck {
    static final String SOURCE_DIR = "src/main/java/dev/aicompanion";
    static final String BASELINE = "tools/catch-baseline.txt";
    static final Set<String> RULES = Set.of("empty-catch", "generic-catch", "tick-swallows-error");
    static final Set<String> GENERIC = Set.of("Exception", "RuntimeException", "Throwable", "Error");
    static final Set<String> FATAL = Set.of("Throwable", "Error");
    static final Set<String> LOGGERS = Set.of("LOGGER", "LOG", "log");

    record Finding(String file, long line, String rule, String detail, String key) {}
    record Entry(Set<String> rules, String reason, int line) {}

    static final class ToolError extends RuntimeException {
        ToolError(String message) { super(message); }
    }

    public static void main(String[] args) {
        int code;
        try {
            code = run(Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize());
        } catch (IOException | RuntimeException error) {
            System.err.println("catch-check: " + (error instanceof ToolError ? "" : error.getClass().getSimpleName() + ": ") + error.getMessage());
            code = 2;
        }
        System.exit(code);
    }

    static int run(Path root) throws IOException {
        Map<String, Entry> baseline = readBaseline(root.resolve(BASELINE));
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

        Map<String, Set<String>> flagged = new HashMap<>();
        all.forEach(f -> flagged.computeIfAbsent(f.key(), k -> new HashSet<>()).add(f.rule()));
        List<Finding> findings = all.stream()
                .filter(f -> !baseline.containsKey(f.key()) || !baseline.get(f.key()).rules().contains(f.rule()))
                .collect(Collectors.toList());
        for (String key : new TreeSet<>(baseline.keySet())) {
            Set<String> actual = flagged.getOrDefault(key, Set.of());
            if (actual.isEmpty()) {
                findings.add(new Finding(BASELINE, baseline.get(key).line(), "baseline-stale", "no flagged catch; remove the entry [" + key + "]", key));
                continue;
            }
            for (String rule : new TreeSet<>(baseline.get(key).rules())) {
                if (!actual.contains(rule)) {
                    findings.add(new Finding(BASELINE, baseline.get(key).line(), "baseline-stale", "the catch no longer breaks " + rule + " [" + key + "]", key));
                }
            }
        }
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

    static Map<String, Entry> readBaseline(Path path) throws IOException {
        Map<String, Entry> entries = new LinkedHashMap<>();
        if (!Files.exists(path)) return entries;
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String where = BASELINE + ":" + (i + 1) + ": ";
            String[] parts = line.split("\\s+", 3);
            if (parts.length < 3 || parts[0].split(":").length != 4) {
                throw new ToolError(where + "expected 'file:Class:method/arity:ordinal rule[,rule] reason'");
            }
            List<String> listed = Arrays.asList(parts[1].split(",", -1));
            if (listed.stream().anyMatch(r -> !RULES.contains(r))) throw new ToolError(where + "empty or unknown rule in '" + parts[1] + "' (use rule,rule without spaces)");
            Set<String> rules = new TreeSet<>(listed);
            if (entries.put(parts[0], new Entry(rules, parts[2], i + 1)) != null) throw new ToolError(where + "duplicate entry " + parts[0]);
        }
        return entries;
    }

    static final class CatchScanner extends TreeScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final String file;
        private final List<Finding> out;
        private final int[] total;
        private final List<String> classes = new ArrayList<>();
        private final List<String> frames = new ArrayList<>();
        private final Map<String, Integer> ordinals = new HashMap<>();

        CatchScanner(CompilationUnitTree unit, SourcePositions positions, String file, List<Finding> out, int[] total) {
            this.unit = unit;
            this.positions = positions;
            this.file = file;
            this.out = out;
            this.total = total;
        }

        private String frame() { return frames.isEmpty() ? "<init>" : frames.get(frames.size() - 1); }

        private void inFrame(String name, Tree tree) {
            frames.add(name);
            try {
                scan(tree, null);
            } finally {
                frames.remove(frames.size() - 1);
            }
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            String name = node.getSimpleName().length() == 0 ? "<anon@" + frame() + ">" : node.getSimpleName().toString();
            classes.add(name);
            try {
                for (Tree member : node.getMembers()) {
                    if (member instanceof BlockTree block) inFrame(block.isStatic() ? "<clinit>" : "<init>", member);
                    else if (member instanceof VariableTree field) {
                        inFrame(field.getModifiers().getFlags().contains(Modifier.STATIC) ? "<clinit>" : "<init>", member);
                    } else scan(member, null);
                }
            } finally {
                classes.remove(classes.size() - 1);
            }
            return null;
        }

        @Override
        public Void visitMethod(MethodTree method, Void unused) {
            inFrame(method.getName() + "/" + method.getParameters().size(), method.getBody());
            return null;
        }

        @Override
        public Void visitCatch(CatchTree node, Void unused) {
            total[0]++;
            String scope = file + ":" + String.join(".", classes) + ":" + frame();
            String key = scope + ":" + ordinals.merge(scope, 1, Integer::sum);
            long line = unit.getLineMap().getLineNumber(positions.getStartPosition(unit, node));
            List<String> types = caughtTypes(node.getParameter().getType());
            String where = "catch (" + String.join(" | ", types) + ") ";
            if (node.getBlock().getStatements().isEmpty()) {
                out.add(new Finding(file, line, "empty-catch", where + "has an empty body [" + key + "]", key));
            }
            if (types.stream().anyMatch(GENERIC::contains) && !handles(node.getBlock(), true)) {
                out.add(new Finding(file, line, "generic-catch", where + "neither logs nor throws [" + key + "]", key));
            }
            Optional<String> tick = frames.stream().filter(m -> m.startsWith("tick")).findFirst();
            if (tick.isPresent() && types.stream().anyMatch(FATAL::contains) && !handles(node.getBlock(), false)) {
                out.add(new Finding(file, line, "tick-swallows-error", where + "inside " + tick.get() + " [" + key + "]", key));
            }
            return super.visitCatch(node, unused);
        }

        private static List<String> caughtTypes(Tree type) {
            List<? extends Tree> alternatives = type instanceof UnionTypeTree union ? union.getTypeAlternatives() : List.of(type);
            return alternatives.stream().map(t -> t.toString().replaceAll("^.*\\.", "")).toList();
        }

        private static boolean handles(BlockTree block, boolean logCounts) {
            boolean[] found = {false};
            new TreeScanner<Void, Void>() {
                @Override
                public Void visitThrow(ThrowTree node, Void unused) {
                    found[0] = true;
                    return null;
                }

                @Override
                public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                    if (node.getMethodSelect() instanceof MemberSelectTree select && logCounts && isLogger(select.getExpression())) {
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

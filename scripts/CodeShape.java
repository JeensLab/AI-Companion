import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Size ceilings for production code, checked against a baseline that may only shrink. A violation that moves with
 * its file or method to another path (the same member: method name and parameters, or file name) is reported as
 * MOVED, not NEW, and --write-baseline records the move; a member that moved and grew is GREW and is refused.
 *
 * <p>Usage, from the repository root:
 * <pre>
 *   java scripts/CodeShape.java                    # compare the tree with tools/code-shape-baseline.txt
 *   java scripts/CodeShape.java --write-baseline   # record the current tree as the new baseline
 *   java scripts/CodeShape.java --selftest         # verdicts of compare() on hand-made moves
 * </pre>
 *
 * <p>Baseline line: {@code file <path> <lines>}, {@code method <path> <Class.name(Types)> <lines>} or
 * {@code params <path> <Class.name(Types)> <count>}. A method spans from its first modifier or annotation to the closing
 * brace, inclusive; lambdas are not methods. Methods are read with the javac Tree API, never with regular expressions.
 */
public final class CodeShape {
    private static final Path SOURCES = Path.of("src/main/java");
    private static final String BASELINE_NAME = "tools/code-shape-baseline.txt";
    private static final Path BASELINE = Path.of(BASELINE_NAME);
    private static final String GAMETEST_DIR = "dev/aicompanion/gametest/";
    private static final int MAX_FILE_LINES = 800;
    private static final int MAX_METHOD_LINES = 80;
    private static final int MAX_PARAMS = 5;
    /** Ceiling for files under gametest/; 0 means none. Set to 600 once the stage 7 split of those files has landed. */
    private static final int GAMETEST_FILE_CEILING = 0;

    public static void main(String[] args) throws Exception {
        boolean write = args.length == 1 && args[0].equals("--write-baseline");
        if (args.length == 1 && args[0].equals("--selftest")) {
            selftest();
            return;
        }
        if (args.length > 0 && !write) {
            System.err.println("Usage: java scripts/CodeShape.java [--write-baseline | --selftest]");
            System.exit(2);
        }
        if (!Files.isDirectory(SOURCES)) {
            System.err.println("Run from the repository root: " + SOURCES + " not found");
            System.exit(2);
        }
        Map<String, Integer> current = measure();
        List<String> problems = compare(readBaseline(), current);
        if (write) {
            writeBaseline(problems, current);
            return;
        }
        problems.forEach(System.out::println);
        System.out.println(problems.isEmpty() ? "PASS code-shape: " + summary(current)
                : "FAIL code-shape: " + problems.size() + " difference(s) from " + BASELINE_NAME);
        System.exit(problems.isEmpty() ? 0 : 1);
    }

    /** The baseline only shrinks: a tree with a new or larger violation cannot be recorded. */
    private static void writeBaseline(List<String> problems, Map<String, Integer> current) throws IOException {
        List<String> growth = problems.stream().filter(line -> line.startsWith("NEW ") || line.startsWith("GREW "))
                .collect(Collectors.toList());
        if (!growth.isEmpty()) {
            growth.forEach(System.out::println);
            System.out.println("REFUSED: fix the code, the baseline only shrinks");
            System.exit(1);
        }
        Files.write(BASELINE, render(current).getBytes(StandardCharsets.UTF_8));
        System.out.println("Wrote " + BASELINE_NAME + ": " + summary(current));
    }

    /** Every violation of the current tree: key "kind path [signature]" to its size. */
    private static Map<String, Integer> measure() throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            files = walk.filter(path -> path.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
        Map<String, Integer> found = new TreeMap<>();
        for (Path file : files) {
            String path = file.toString().replace('\\', '/');
            int lines = Files.readAllLines(file, StandardCharsets.UTF_8).size();
            int ceiling = path.contains(GAMETEST_DIR) ? GAMETEST_FILE_CEILING : MAX_FILE_LINES;
            if (ceiling > 0 && lines > ceiling) {
                found.put("file " + path, lines);
            }
        }
        scanMethods(files, found);
        return found;
    }

    private static void scanMethods(List<Path> files, Map<String, Integer> found) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = manager.getJavaFileObjectsFromPaths(files);
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null, units);
            SourcePositions positions = Trees.instance(task).getSourcePositions();
            List<CompilationUnitTree> trees = new ArrayList<>();
            task.parse().forEach(trees::add);
            failOnSyntaxErrors(diagnostics);
            for (CompilationUnitTree unit : trees) {
                new MethodScanner(unit, positions, found).scan(unit, null);
            }
        }
    }

    /** A partial tree would make the sizes wrong, so a file that does not parse stops the check. */
    private static void failOnSyntaxErrors(DiagnosticCollector<JavaFileObject> diagnostics) {
        List<Diagnostic<? extends JavaFileObject>> errors = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).collect(Collectors.toList());
        if (errors.isEmpty()) {
            return;
        }
        errors.forEach(error -> System.out.println("SYNTAX " + error.getSource().getName() + ":" + error.getLineNumber()
                + ": " + error.getMessage(null)));
        System.out.println("FAIL code-shape: " + errors.size() + " syntax error(s); sizes cannot be measured");
        System.exit(2);
    }

    private static final class MethodScanner extends TreePathScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final Map<String, Integer> found;
        private final Deque<String> classes = new ArrayDeque<>();
        private final String path;

        MethodScanner(CompilationUnitTree unit, SourcePositions positions, Map<String, Integer> found) {
            this.unit = unit;
            this.positions = positions;
            this.found = found;
            this.path = Path.of(unit.getSourceFile().toUri()).toString().replace('\\', '/')
                    .replaceFirst("^.*?/(src/main/java/)", "$1");
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            String name = tree.getSimpleName().toString();
            classes.addLast(name.isEmpty() ? "<anonymous>" : name);
            try {
                return super.visitClass(tree, unused);
            } finally {
                classes.removeLast();
            }
        }

        @Override
        public Void visitMethod(MethodTree tree, Void unused) {
            String name = tree.getName().contentEquals("<init>") ? classes.peekLast() : tree.getName().toString();
            String types = tree.getParameters().stream().map(parameter -> parameter.getType().toString())
                    .collect(Collectors.joining(","));
            String signature = (String.join(".", classes) + "." + name + "(" + types + ")").replaceAll("\\s+", "");
            int length = lineOf(positions.getEndPosition(unit, tree)) - lineOf(positions.getStartPosition(unit, tree)) + 1;
            if (length > MAX_METHOD_LINES) {
                record("method " + path + " " + signature, length);
            }
            if (tree.getParameters().size() > MAX_PARAMS) {
                record("params " + path + " " + signature, tree.getParameters().size());
            }
            return super.visitMethod(tree, unused);
        }

        /** Two methods with the same signature (anonymous classes) get a "#n" suffix in source order. */
        private void record(String key, int value) {
            String unique = key;
            for (int copy = 2; found.containsKey(unique); copy++) {
                unique = key + "#" + copy;
            }
            found.put(unique, value);
        }

        private int lineOf(long position) {
            return (int) unit.getLineMap().getLineNumber(position);
        }
    }

    private static Map<String, Integer> readBaseline() throws IOException {
        Map<String, Integer> baseline = new TreeMap<>();
        if (!Files.exists(BASELINE)) {
            return baseline;
        }
        for (String line : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            int space = line.lastIndexOf(' ');
            baseline.put(line.substring(0, space), Integer.parseInt(line.substring(space + 1)));
        }
        return baseline;
    }

    private static List<String> compare(Map<String, Integer> baseline, Map<String, Integer> current) {
        List<String> problems = new ArrayList<>();
        Map<String, String> moved = moves(baseline, current);
        for (String key : new TreeSet<>(current.keySet())) {
            Integer allowed = baseline.get(key);
            int now = current.get(key);
            String from = moved.get(key);
            if (from != null) {
                int was = baseline.get(from);
                problems.add((now > was ? "GREW " : "MOVED ") + from + " -> " + key + " " + was + " -> " + now
                        + (now > was ? ": moved and grew; do not make it larger; shrink it"
                        : ": the same member at a new path; record the move with --write-baseline and commit it"));
            } else if (allowed == null) {
                problems.add("NEW " + key + " " + now + ": over the ceiling and not in the baseline; make it smaller");
            } else if (now > allowed) {
                problems.add("GREW " + key + " " + allowed + " -> " + now + ": do not make it larger; shrink it");
            } else if (now < allowed) {
                problems.add("SHRANK " + key + " " + allowed + " -> " + now
                        + ": lower the baseline with --write-baseline and commit it");
            }
        }
        for (String key : new TreeSet<>(baseline.keySet())) {
            if (!current.containsKey(key) && !moved.containsValue(key)) {
                problems.add("FIXED " + key + ": gone or under the ceiling; remove it with --write-baseline and commit");
            }
        }
        return problems;
    }

    /**
     * Violations that left one path and appeared at another with the same member: a file or a method moved (a split
     * test class, a helper taken into a package). Candidates pair in key order, so the pairing is deterministic, and
     * the size is judged afterwards by compare(). Keyed by the new key, valued by the old one.
     */
    private static Map<String, String> moves(Map<String, Integer> baseline, Map<String, Integer> current) {
        Map<String, Deque<String>> gone = new HashMap<>();
        for (String key : new TreeSet<>(baseline.keySet())) {
            if (!current.containsKey(key)) {
                gone.computeIfAbsent(member(key), member -> new ArrayDeque<>()).add(key);
            }
        }
        Map<String, String> moved = new TreeMap<>();
        for (String key : new TreeSet<>(current.keySet())) {
            if (baseline.containsKey(key)) {
                continue;
            }
            Deque<String> candidates = gone.get(member(key));
            if (candidates != null && !candidates.isEmpty()) {
                moved.put(key, candidates.poll());
            }
        }
        return moved;
    }

    /**
     * The key without its directory and class: "file Name.java", "method name(params)" or "params name(params)".
     * Paths never contain spaces: the tool measures src/main/java, whose files are Java identifiers.
     */
    private static String member(String key) {
        int kindEnd = key.indexOf(' ');
        int pathEnd = key.indexOf(' ', kindEnd + 1);
        String kind = key.substring(0, kindEnd);
        if (pathEnd < 0) {
            return kind + " " + key.substring(key.lastIndexOf('/') + 1);
        }
        String signature = key.substring(pathEnd + 1);
        int paren = signature.indexOf('(');
        int dot = paren < 0 ? signature.lastIndexOf('.') : signature.lastIndexOf('.', paren);
        return kind + " " + signature.substring(dot + 1);
    }

    /** compare() on hand-made maps: a move, twins that both move, a move that grew, one that shrank, and no change. */
    private static void selftest() {
        Map<String, Integer> base = new TreeMap<>();
        base.put("method a/A.java A.run(int)", 95);
        base.put("params a/B.java B.box(int,int,int,int,int,int)", 6);
        base.put("params a/C.java C.box(int,int,int,int,int,int)", 6);
        base.put("method a/D.java D.go()", 90);
        base.put("file a/E.java", 900);
        Map<String, Integer> moved = new TreeMap<>();
        moved.put("method p/A.java A.run(int)", 95);
        moved.put("params p/B.java B.box(int,int,int,int,int,int)", 6);
        moved.put("params p/C.java C.box(int,int,int,int,int,int)", 6);
        moved.put("method p/D.java D.go()", 99);
        moved.put("file p/E.java", 850);
        List<String> expected = List.of(
                "MOVED file a/E.java -> file p/E.java 900 -> 850",
                "MOVED method a/A.java A.run(int) -> method p/A.java A.run(int) 95 -> 95",
                "GREW method a/D.java D.go() -> method p/D.java D.go() 90 -> 99",
                "MOVED params a/B.java B.box(int,int,int,int,int,int) -> params p/B.java B.box(int,int,int,int,int,int) 6 -> 6",
                "MOVED params a/C.java C.box(int,int,int,int,int,int) -> params p/C.java C.box(int,int,int,int,int,int) 6 -> 6");
        List<String> got = compare(base, moved).stream().map(line -> line.substring(0, line.indexOf(':')))
                .collect(Collectors.toList());
        List<String> same = compare(base, base);
        if (!got.equals(expected) || !same.isEmpty()) {
            System.out.println("expected: " + expected + "\ngot:      " + got + "\nunchanged tree: " + same);
            System.out.println("FAIL code-shape selftest");
            System.exit(1);
        }
        System.out.println("code-shape selftest: 5 verdicts on moved members, ok");
    }

    private static String render(Map<String, Integer> violations) {
        return violations.entrySet().stream().map(entry -> entry.getKey() + " " + entry.getValue() + "\n")
                .collect(Collectors.joining());
    }

    private static String summary(Map<String, Integer> violations) {
        long files = violations.keySet().stream().filter(key -> key.startsWith("file ")).count();
        long methods = violations.keySet().stream().filter(key -> key.startsWith("method ")).count();
        long params = violations.keySet().stream().filter(key -> key.startsWith("params ")).count();
        return files + " file(s), " + methods + " method(s), " + params + " parameter list(s) in the baseline";
    }
}

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Anti-tautology check: every {@code @GameTest} method must reach a call that can fail the test, either in its own body or
 * through the methods it calls (same class, other test classes, support classes), lambdas included. A test that only calls
 * {@code succeed()} or {@code succeedWhen(() -> {})} proves nothing and is reported.
 *
 * <p>The sources are only parsed, never attributed (attribution needs the Minecraft classpath), so a call is resolved by its
 * simple name, in this order: {@code Helper.name(..)} / {@code Helper::name} with a scanned class {@code Helper} goes to that
 * class; otherwise a method of that name in the caller's own class; otherwise in the caller's file; otherwise in other files,
 * where a name defined by several classes counts only if EVERY one of those classes has a method of that name that asserts
 * (whichever class the receiver is, the call asserts). So an assertion inside one foreign {@code setup} cannot rescue a test
 * when another {@code setup} elsewhere asserts nothing. Overloads of one class are all linked, and a receiver variable is not
 * tracked ({@code x.name(..)} resolves like {@code name(..)}): both can only make the check more lenient.
 *
 * <p>Usage, from the repository root: {@code java scripts/TestQuality.java [root | --selftest]}. Exit 1 when a method without a
 * reachable assertion is not listed in {@code tools/test-quality-baseline.txt} (one {@code Class.method # reason} per line);
 * exit 2 on a usage or I/O error. {@code --selftest} plants a tautology in a temporary tree and expects it to be found.
 */
public final class TestQuality {
    private static final String SOURCE_ROOT = "src/main/java";
    private static final String GAMETEST_DIR = "/dev/aicompanion/gametest/";
    private static final String BASELINE = "tools/test-quality-baseline.txt";

    // Calls that fail a GameTest when the observed fact is wrong. Derived from GameTestHelper and GameTestSequence in
    // Minecraft 1.21.1: every assert* method, the succeedWhen* variants that wait for a fact and time out otherwise, and the
    // explicit fail calls. The Runnable-taking ones (succeed, succeedIf, succeedWhen, succeedOnTickWhen, runAtTickTime,
    // runAfterDelay, failIf, failIfEver, onEachTick, thenWaitUntil, thenExecute*) are not here on purpose: they only count
    // through an assertion inside the lambda they receive.
    private static final Set<String> ASSERTING_CALLS = Set.of(
            "assertBlockPresent", "assertBlockNotPresent", "assertBlock", "assertBlockProperty", "assertBlockState",
            "assertBlockEntityData", "assertRedstoneSignal", "assertEntityPresent", "assertEntitiesPresent",
            "assertEntityInstancePresent", "assertItemEntityCountIs", "assertItemEntityPresent",
            "assertItemEntityNotPresent", "assertEntityNotPresent", "assertEntityTouching", "assertEntityNotTouching",
            "assertEntityData", "assertEntityIsHolding", "assertEntityInventoryContains", "assertContainerEmpty",
            "assertContainerContains", "assertSameBlockStates", "assertSameBlockState", "assertAtTickTimeContainerContains",
            "assertAtTickTimeContainerEmpty", "assertEntityPosition", "assertEntityProperty",
            "assertLivingEntityHasMobEffect", "assertTrue", "assertFalse", "assertValueEqual",
            "succeedWhenBlockPresent", "succeedWhenEntityData", "succeedWhenEntityPresent", "succeedWhenEntityNotPresent",
            "fail", "thenFail");

    private record Method(String owner, String name, String file, long line, boolean isTest) {
        String key() {
            return owner + "." + name;
        }
    }

    private record Call(String name, String qualifier, String field) { }

    // Declared types of class-level fields by field name, over all scanned files (a name may have several types).
    private static final Map<String, Set<String>> FIELD_TYPES = new HashMap<>();

    private static final class Body {
        final Method method;
        final Set<Call> calls = new HashSet<>();
        boolean throwsDirectly;

        Body(Method method) {
            this.method = method;
        }
    }

    private record Analysis(List<Body> tests, List<Body> unasserted) { }

    public static void main(String[] args) {
        try {
            if (args.length > 0 && args[0].equals("--selftest")) System.exit(selfTest());
            if (args.length > 1) throw new IllegalArgumentException("usage: TestQuality.java [root | --selftest]");
            System.exit(check(Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize()));
        } catch (IOException | RuntimeException e) {
            System.err.println("test-quality: error: " + e);
            System.exit(2);
        }
    }

    private static int check(Path root) throws IOException {
        Analysis analysis = analyze(root);
        Map<String, String> baseline = readBaseline(root.resolve(BASELINE));
        List<Body> findings = new ArrayList<>();
        Set<String> baselined = new TreeSet<>();
        for (Body test : analysis.unasserted) {
            if (baseline.containsKey(test.method.key())) baselined.add(test.method.key());
            else findings.add(test);
        }
        for (Body finding : findings) {
            System.out.println(finding.method.file + ":" + finding.method.line + " " + finding.method.key() + ": no reachable assertion");
        }
        int stale = 0;
        for (String key : baseline.keySet()) {
            if (baselined.contains(key)) continue;
            stale++;
            System.out.println(BASELINE + ": " + key + " is listed but has a reachable assertion or does not exist; remove it");
        }
        if (!baselined.isEmpty()) System.out.println("test-quality: " + baselined.size() + " baselined: " + baselined);
        System.out.println("test-quality: " + analysis.tests.size() + " @GameTest methods, " + findings.size() + " without a reachable assertion");
        return findings.isEmpty() && stale == 0 ? 0 : 1;
    }

    private static Analysis analyze(Path root) throws IOException {
        List<Body> bodies = scan(root);
        Map<String, List<Body>> byName = new LinkedHashMap<>();
        for (Body body : bodies) byName.computeIfAbsent(body.method.name, k -> new ArrayList<>()).add(body);
        Set<String> classes = bodies.stream().map(b -> b.method.owner).collect(Collectors.toSet());
        Set<Body> asserting = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Body body : bodies) {
                if (asserting.contains(body) || !asserts(body, byName, classes, asserting)) continue;
                asserting.add(body);
                changed = true;
            }
        }
        List<Body> tests = bodies.stream().filter(b -> b.method.isTest).toList();
        return new Analysis(tests, tests.stream().filter(t -> !asserting.contains(t)).toList());
    }

    private static boolean asserts(Body body, Map<String, List<Body>> byName, Set<String> classes, Set<Body> asserting) {
        if (body.throwsDirectly) return true;
        for (Call call : body.calls) {
            if (ASSERTING_CALLS.contains(call.name)) return true;
            List<List<Body>> groups = resolve(body, call, byName.getOrDefault(call.name, List.of()), classes);
            if (!groups.isEmpty() && groups.stream().allMatch(group -> group.stream().anyMatch(asserting::contains))) return true;
        }
        return false;
    }

    // Groups of possible callees; the call asserts when every group has a method that asserts. Same class, caller's file and a
    // named class are one group (any overload will do). Elsewhere each class that defines the name is a group, so an ambiguous
    // name counts only when it asserts whichever class the receiver is.
    // The caller itself is not a candidate: a method that forwards to {@code receiver.sameName(..)} would otherwise resolve to itself.
    private static List<List<Body>> resolve(Body caller, Call call, List<Body> allNamed, Set<String> classes) {
        List<Body> named = filter(allNamed, b -> b != caller);
        if (call.qualifier != null && classes.contains(call.qualifier)) return one(filter(named, b -> b.method.owner.equals(call.qualifier)));
        Set<String> declared = call.field == null ? null : FIELD_TYPES.get(call.field);
        if (declared != null && named.stream().anyMatch(b -> declared.contains(b.method.owner))) {
            return new ArrayList<>(filter(named, b -> declared.contains(b.method.owner)).stream().collect(Collectors.groupingBy(b -> b.method.owner)).values());
        }
        List<Body> sameClass = filter(named, b -> b.method.owner.equals(caller.method.owner) && b.method.file.equals(caller.method.file));
        if (!sameClass.isEmpty()) return one(sameClass);
        List<Body> sameFile = filter(named, b -> b.method.file.equals(caller.method.file));
        if (!sameFile.isEmpty()) return one(sameFile);
        return new ArrayList<>(named.stream().collect(Collectors.groupingBy(b -> b.method.file + "#" + b.method.owner)).values());
    }

    private static List<List<Body>> one(List<Body> group) {
        return group.isEmpty() ? List.of() : List.of(group);
    }

    private static List<Body> filter(List<Body> bodies, Predicate<Body> keep) {
        return bodies.stream().filter(keep).toList();
    }

    private static List<Body> scan(Path root) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root.resolve(SOURCE_ROOT))) {
            files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        // The graph covers gametest/ and any other file that declares a @GameTest (two live under entity/).
        List<Path> wanted = new ArrayList<>();
        for (Path file : files) {
            String normalized = file.toString().replace('\\', '/');
            if (normalized.contains(GAMETEST_DIR) || Files.readString(file).contains("@GameTest")) wanted.add(file);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> units = manager.getJavaFileObjectsFromPaths(wanted);
            JavacTask task = (JavacTask) compiler.getTask(null, manager, null, List.of("-proc:none"), null, units);
            SourcePositions positions = Trees.instance(task).getSourcePositions();
            List<Body> bodies = new ArrayList<>();
            FIELD_TYPES.clear();
            for (CompilationUnitTree unit : task.parse()) {
                new Collector(root, unit, positions, bodies).scan(unit, null);
            }
            return bodies;
        }
    }

    private static Map<String, String> readBaseline(Path file) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        if (!Files.exists(file)) return entries;
        int number = 0;
        for (String raw : Files.readAllLines(file)) {
            number++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int hash = line.indexOf('#');
            if (hash < 0 || line.substring(hash + 1).isBlank()) {
                throw new IllegalStateException(BASELINE + ":" + number + ": every entry needs a '# reason'");
            }
            entries.put(line.substring(0, hash).strip(), line.substring(hash + 1).strip());
        }
        return entries;
    }

    // A tiny tree: two tautologies (one rescued only by a foreign, ambiguous setup) and three tests that do assert.
    private static int selfTest() throws IOException {
        Path root = Files.createTempDirectory("test-quality-selftest");
        try {
            Path dir = Files.createDirectories(root.resolve(SOURCE_ROOT + GAMETEST_DIR));
            String test = "    @GameTest public static void ";
            Files.writeString(dir.resolve("Tautology.java"), "class Tautology {\n" + test + "onlySucceeds(GameTestHelper h) { h.succeed(); }\n"
                    + test + "foreignSetup(GameTestHelper h) { setup(h); h.succeed(); }\n}\n");
            Files.writeString(dir.resolve("Honest.java"), "class Honest {\n" + test + "asserts(GameTestHelper h) { h.assertTrue(true, \"m\"); }\n"
                    + test + "ownHelper(GameTestHelper h) { check(h); }\n    static void check(GameTestHelper h) { h.assertValueEqual(1, 1, \"m\"); }\n"
                    + test + "qualified(GameTestHelper h) { Other.setup(h); }\n}\n");
            Files.writeString(dir.resolve("Other.java"), "class Other { static void setup(GameTestHelper h) { h.assertTrue(true, \"m\"); } }\n");
            Files.writeString(dir.resolve("Third.java"), "class Third { static void setup(GameTestHelper h) { } }\n");
            Analysis analysis = analyze(root);
            Set<String> found = analysis.unasserted.stream().map(b -> b.method.key()).collect(Collectors.toCollection(TreeSet::new));
            boolean ok = analysis.tests.size() == 5 && found.equals(Set.of("Tautology.onlySucceeds", "Tautology.foreignSetup"));
            System.out.println("test-quality selftest: " + analysis.tests.size() + " planted tests, flagged " + found + (ok ? ": ok" : ": WRONG"));
            return ok ? 0 : 1;
        } finally {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static final class Collector extends TreeScanner<Void, Void> {
        private final Path root;
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final List<Body> out;
        private final Deque<String> classes = new ArrayDeque<>();
        // Calls inside a nested class or lambda count for every method that encloses them.
        private final Deque<Body> open = new ArrayDeque<>();

        Collector(Path root, CompilationUnitTree unit, SourcePositions positions, List<Body> out) {
            this.root = root;
            this.unit = unit;
            this.positions = positions;
            this.out = out;
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            classes.push(node.getSimpleName().toString());
            try {
                return super.visitClass(node, unused);
            } finally {
                classes.pop();
            }
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            if (node.getBody() == null || classes.isEmpty()) return super.visitMethod(node, unused);
            long line = unit.getLineMap().getLineNumber(positions.getStartPosition(unit, node));
            String file = root.relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\', '/');
            Body body = new Body(new Method(classes.peek(), node.getName().toString(), file, line, isGameTest(node)));
            out.add(body);
            open.push(body);
            try {
                return super.visitMethod(node, unused);
            } finally {
                open.pop();
            }
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            ExpressionTree select = node.getMethodSelect();
            if (select instanceof IdentifierTree id) call(id.getName().toString(), null, null);
            else if (select instanceof MemberSelectTree member) call(member.getIdentifier().toString(), qualifier(member.getExpression()), field(member.getExpression()));
            return super.visitMethodInvocation(node, unused);
        }

        @Override
        public Void visitMemberReference(MemberReferenceTree node, Void unused) {
            call(node.getName().toString(), qualifier(node.getQualifierExpression()), field(node.getQualifierExpression()));
            return super.visitMemberReference(node, unused);
        }

        // A field of a scanned class (not a local) has a declared type: that is what narrows {@code a.script.run(..)} to MarathonScript.
        @Override
        public Void visitVariable(VariableTree node, Void unused) {
            if (open.isEmpty() && !classes.isEmpty()) {
                String type = node.getType() == null ? "" : node.getType().toString().replaceAll("<.*", "").replaceAll("^.*\\.", "");
                FIELD_TYPES.computeIfAbsent(node.getName().toString(), k -> new HashSet<>()).add(type);
            }
            return super.visitVariable(node, unused);
        }

        @Override
        public Void visitThrow(ThrowTree node, Void unused) {
            for (Body body : open) body.throwsDirectly = true;
            return super.visitThrow(node, unused);
        }

        private void call(String name, String qualifier, String field) {
            for (Body body : open) body.calls.add(new Call(name, qualifier, field));
        }

        private static String field(ExpressionTree expression) {
            if (expression instanceof MemberSelectTree select) return select.getIdentifier().toString();
            return expression instanceof IdentifierTree id ? id.getName().toString() : null;
        }

        private static String qualifier(ExpressionTree expression) {
            return expression instanceof IdentifierTree id ? id.getName().toString() : null;
        }

        private static boolean isGameTest(MethodTree node) {
            for (AnnotationTree annotation : node.getModifiers().getAnnotations()) {
                String name = annotation.getAnnotationType().toString();
                if (name.equals("GameTest") || name.endsWith(".GameTest")) return true;
            }
            return false;
        }
    }
}

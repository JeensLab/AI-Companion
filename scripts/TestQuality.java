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
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
 * <p>The sources are only parsed, never attributed (attribution needs the Minecraft classpath). A call is therefore matched to
 * methods by simple name alone: {@code name(..)}, {@code this.name(..)}, {@code Helper.name(..)} and {@code Helper::name} all
 * link to every method called {@code name} under the scanned files. Linking too much can only make the check more lenient,
 * never produce a false finding.
 *
 * <p>Usage, from the repository root: {@code java scripts/TestQuality.java [root]}. Exit 1 when a method without a reachable
 * assertion is not listed in {@code tools/test-quality-baseline.txt} (one {@code Class.method # reason} per line).
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

    private static final class Body {
        final Method method;
        final Set<String> calls = new HashSet<>();
        boolean throwsDirectly;

        Body(Method method) {
            this.method = method;
        }
    }

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        List<Body> bodies = scan(root);
        Map<String, List<Body>> byName = new LinkedHashMap<>();
        for (Body body : bodies) byName.computeIfAbsent(body.method.name, k -> new ArrayList<>()).add(body);

        Set<Body> asserting = assertingBodies(bodies, byName);
        List<Body> tests = bodies.stream().filter(b -> b.method.isTest).toList();
        Map<String, String> baseline = readBaseline(root.resolve(BASELINE));
        List<Body> findings = new ArrayList<>();
        Set<String> baselined = new TreeSet<>();
        for (Body test : tests) {
            if (asserting.contains(test)) continue;
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
        System.out.println("test-quality: " + tests.size() + " @GameTest methods, " + findings.size() + " without a reachable assertion");
        System.exit(findings.isEmpty() && stale == 0 ? 0 : 1);
    }

    // Fixpoint: a body asserts when it throws, calls an asserting API, or calls any method that asserts.
    private static Set<Body> assertingBodies(List<Body> bodies, Map<String, List<Body>> byName) {
        Set<Body> asserting = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Body body : bodies) {
                if (asserting.contains(body) || !asserts(body, byName, asserting)) continue;
                asserting.add(body);
                changed = true;
            }
        }
        return asserting;
    }

    private static boolean asserts(Body body, Map<String, List<Body>> byName, Set<Body> asserting) {
        if (body.throwsDirectly) return true;
        for (String call : body.calls) {
            if (ASSERTING_CALLS.contains(call)) return true;
            for (Body callee : byName.getOrDefault(call, List.of())) if (asserting.contains(callee)) return true;
        }
        return false;
    }

    private static List<Body> scan(Path root) throws IOException {
        Path sources = root.resolve(SOURCE_ROOT);
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sources)) {
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
            if (select instanceof IdentifierTree id) call(id.getName().toString());
            else if (select instanceof MemberSelectTree member) call(member.getIdentifier().toString());
            return super.visitMethodInvocation(node, unused);
        }

        @Override
        public Void visitMemberReference(MemberReferenceTree node, Void unused) {
            call(node.getName().toString());
            return super.visitMemberReference(node, unused);
        }

        @Override
        public Void visitThrow(ThrowTree node, Void unused) {
            for (Body body : open) body.throwsDirectly = true;
            return super.visitThrow(node, unused);
        }

        private void call(String name) {
            for (Body body : open) body.calls.add(name);
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

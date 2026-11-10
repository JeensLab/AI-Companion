import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseLabelTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConstantCaseLabelTree;
import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Fails when a {@code switch} over an enum declared in this mod hides a new constant: a {@code default} branch, or a
 * constant that no case lists. javac already forces every constant into a switch expression without {@code default},
 * but it does not check switch statements, so a new constant would be silently ignored there. Enums of Minecraft and
 * NeoForge, and switches over strings or numbers, are out of scope.
 *
 * <p>Usage, from the repository root: {@code java scripts/SwitchCheck.java}. Exit code 0 means clean, 1 means findings,
 * 2 means the check itself could not run (a source does not parse, no project enum was found, an internal error).
 *
 * <p>Method: the sources are only parsed, never attributed. Attribution needs the Minecraft classpath, and without it
 * javac fails on some switch expressions. The enum behind a switch is found from its case labels instead: the labels
 * must all be constants of one enum declared under dev.aicompanion. Labels that fit no project enum belong to a
 * Minecraft enum, a string or a number and are skipped. Labels that fit several project enums are narrowed by a
 * qualified label ({@code Phase.CRAFT}) or by the declared type of the selector; if that still leaves several, the
 * switch is reported as ambiguous so that nothing passes unchecked. A switch with no constant label (only
 * {@code default}) is checked only through the declared type of the selector.
 *
 * <p>Known gap: the declared type is looked up only for a selector that is a plain name or {@code this.name}, among the
 * parameters, locals and fields of the same file. A method-call selector, a {@code var} local or a lambda parameter
 * has no declared type here, so such a switch is checked by its labels alone and a default-only one is skipped.
 * A pattern binding ({@code instanceof Foo name}, {@code case Foo name}) is not seen as a declaration, so a field of
 * the same name and of a project enum type shadows it and a default-only switch over the binding is a false finding;
 * rename one of them. A {@code case null} label makes the switch non-enum-shaped and it is skipped.
 */
public final class SwitchCheck {
    private static final String ROOT = "src/main/java/dev/aicompanion";

    public static void main(String[] args) {
        int code;
        try {
            code = run();
        } catch (Exception | Error failure) {
            System.err.println("switch-check: internal error: " + failure);
            code = 2;
        }
        System.exit(code);
    }

    private static int run() throws Exception {
        Path root = Paths.get(ROOT);
        if (!Files.isDirectory(root)) {
            System.err.println("switch-check: " + ROOT + " not found, run from the repository root");
            return 2;
        }
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/gametest/"))
                    .sorted().forEach(sources::add);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("switch-check: no system Java compiler, run with a JDK");
            return 2;
        }
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromPaths(sources);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics, List.of("-proc:none"), null, units);
        List<CompilationUnitTree> parsed = new ArrayList<>();
        task.parse().forEach(parsed::add);
        Set<String> errors = new LinkedHashSet<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errors.add("switch-check: " + diagnostic.getSource().getName() + ":" + diagnostic.getLineNumber()
                        + " " + diagnostic.getMessage(null));
            }
        }
        if (!errors.isEmpty()) {
            errors.forEach(System.err::println);
            return 2;
        }

        Map<String, List<String>> enums = new LinkedHashMap<>();
        for (CompilationUnitTree unit : parsed) {
            new EnumCollector(unit, enums).scan(unit, null);
        }
        if (enums.isEmpty()) {
            System.err.println("switch-check: no project enums found in " + sources.size() + " sources, the scan did not see the tree");
            return 2;
        }
        Checker checker = new Checker(Trees.instance(task).getSourcePositions(), enums);
        for (CompilationUnitTree unit : parsed) {
            checker.unit = unit;
            checker.scan(unit, null);
        }
        checker.findings.forEach(System.out::println);
        System.out.println("switch-check: " + enums.size() + " project enums, " + checker.checked + " switches checked, "
                + checker.findings.size() + " findings");
        return checker.findings.isEmpty() ? 0 : 1;
    }

    private static String packageOf(CompilationUnitTree unit) {
        return unit.getPackageName() == null ? "" : unit.getPackageName() + ".";
    }

    /** Collects every enum declared in the sources: qualified name (outer classes included) to its constant names. */
    private static final class EnumCollector extends TreeScanner<Void, Void> {
        private final Map<String, List<String>> enums;
        private final List<String> outer = new ArrayList<>();
        private final String prefix;

        EnumCollector(CompilationUnitTree unit, Map<String, List<String>> enums) {
            this.enums = enums;
            this.prefix = packageOf(unit);
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            outer.add(node.getSimpleName().toString());
            if (node.getKind() == Tree.Kind.ENUM) {
                List<String> constants = new ArrayList<>();
                for (Tree member : node.getMembers()) {
                    if (member instanceof VariableTree variable && variable.getType() != null
                            && variable.getType().toString().equals(node.getSimpleName().toString())
                            && variable.getInitializer() instanceof NewClassTree) {
                        constants.add(variable.getName().toString());
                    }
                }
                enums.put(prefix + String.join(".", outer), constants);
            }
            super.visitClass(node, unused);
            outer.remove(outer.size() - 1);
            return null;
        }
    }

    private static final class Checker extends TreePathScanner<Void, Void> {
        final List<String> findings = new ArrayList<>();
        final SourcePositions positions;
        final Map<String, List<String>> enums;
        CompilationUnitTree unit;
        int checked;

        Checker(SourcePositions positions, Map<String, List<String>> enums) {
            this.positions = positions;
            this.enums = enums;
        }

        @Override
        public Void visitSwitch(SwitchTree node, Void unused) {
            check(node, node.getExpression(), node.getCases());
            return super.visitSwitch(node, unused);
        }

        @Override
        public Void visitSwitchExpression(SwitchExpressionTree node, Void unused) {
            check(node, node.getExpression(), node.getCases());
            return super.visitSwitchExpression(node, unused);
        }

        private void check(Tree node, ExpressionTree selector, List<? extends CaseTree> cases) {
            Set<String> listed = new LinkedHashSet<>();
            Set<String> qualifiers = new LinkedHashSet<>();
            boolean hasDefault = false;
            boolean isEnumShaped = true;
            for (CaseTree branch : cases) {
                for (CaseLabelTree label : branch.getLabels()) {
                    if (label instanceof DefaultCaseLabelTree) {
                        hasDefault = true;
                    } else if (label instanceof ConstantCaseLabelTree constant) {
                        ExpressionTree expression = constant.getConstantExpression();
                        if (expression instanceof IdentifierTree identifier) {
                            listed.add(identifier.getName().toString());
                        } else if (expression instanceof MemberSelectTree select) {
                            listed.add(select.getIdentifier().toString());
                            qualifiers.add(select.getExpression().toString());
                        } else {
                            isEnumShaped = false;
                        }
                    } else {
                        isEnumShaped = false;
                    }
                }
            }
            if (!isEnumShaped) {
                return;
            }
            List<String> candidates = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : enums.entrySet()) {
                if (entry.getValue().containsAll(listed) && qualifiersFit(entry.getKey(), qualifiers)) {
                    candidates.add(entry.getKey());
                }
            }
            String declared = declaredTypeOf(selector);
            if (listed.isEmpty()) {
                candidates = declared == null ? List.of() : byDeclaredType(candidates, declared);
            } else if (candidates.size() > 1 && declared != null) {
                List<String> narrowed = byDeclaredType(candidates, declared);
                candidates = narrowed.isEmpty() ? candidates : narrowed;
            }
            if (candidates.isEmpty()) {
                return;
            }
            String where = location(node);
            if (candidates.size() > 1) {
                findings.add(where + " ambiguous: labels fit " + String.join(", ", candidates));
                return;
            }
            checked++;
            String enumName = candidates.get(0);
            List<String> missing = new ArrayList<>(enums.get(enumName));
            missing.removeAll(listed);
            if (hasDefault) {
                findings.add(where + " " + enumName + " has default");
            }
            if (!missing.isEmpty()) {
                findings.add(where + " " + enumName + " Missing: " + String.join(", ", missing));
            }
        }

        private static boolean qualifiersFit(String enumName, Set<String> qualifiers) {
            for (String qualifier : qualifiers) {
                if (!enumName.equals(qualifier) && !enumName.endsWith("." + qualifier)) {
                    return false;
                }
            }
            return true;
        }

        private static List<String> byDeclaredType(List<String> candidates, String declared) {
            List<String> matching = new ArrayList<>();
            for (String candidate : candidates) {
                if (candidate.equals(declared) || candidate.endsWith("." + declared)) {
                    matching.add(candidate);
                }
            }
            return matching;
        }

        /**
         * The declared type of a selector that is a plain name or {@code this.name}: the nearest enclosing declaration of
         * that name among parameters, locals and fields. Null when there is none in the file, or the type is inferred.
         */
        private String declaredTypeOf(ExpressionTree selector) {
            Tree tree = selector;
            while (tree instanceof ParenthesizedTree parenthesized) {
                tree = parenthesized.getExpression();
            }
            String name;
            if (tree instanceof IdentifierTree identifier) {
                name = identifier.getName().toString();
            } else if (tree instanceof MemberSelectTree select && select.getExpression() instanceof IdentifierTree owner
                    && owner.getName().contentEquals("this")) {
                name = select.getIdentifier().toString();
            } else {
                return null;
            }
            for (TreePath path = getCurrentPath(); path != null; path = path.getParentPath()) {
                VariableTree found = declarationIn(path.getLeaf(), name);
                if (found != null) {
                    Tree type = found.getType();
                    return type == null || type.toString().equals("var") ? null : type.toString();
                }
            }
            return null;
        }

        private static VariableTree declarationIn(Tree scope, String name) {
            List<VariableTree> variables = new ArrayList<>();
            if (scope instanceof MethodTree method) {
                variables.addAll(method.getParameters());
            } else if (scope instanceof LambdaExpressionTree lambda) {
                variables.addAll(lambda.getParameters());
            } else if (scope instanceof BlockTree block) {
                for (StatementTree statement : block.getStatements()) {
                    if (statement instanceof VariableTree variable) {
                        variables.add(variable);
                    }
                }
            } else if (scope instanceof ForLoopTree loop) {
                for (StatementTree statement : loop.getInitializer()) {
                    if (statement instanceof VariableTree variable) {
                        variables.add(variable);
                    }
                }
            } else if (scope instanceof EnhancedForLoopTree loop) {
                variables.add(loop.getVariable());
            } else if (scope instanceof CatchTree handler) {
                variables.add(handler.getParameter());
            } else if (scope instanceof TryTree attempt) {
                for (Tree resource : attempt.getResources()) {
                    if (resource instanceof VariableTree variable) {
                        variables.add(variable);
                    }
                }
            } else if (scope instanceof ClassTree type) {
                for (Tree member : type.getMembers()) {
                    if (member instanceof VariableTree variable) {
                        variables.add(variable);
                    }
                }
            }
            for (VariableTree variable : variables) {
                if (variable.getName().contentEquals(name)) {
                    return variable;
                }
            }
            return null;
        }

        private String location(Tree node) {
            long line = unit.getLineMap().getLineNumber(positions.getStartPosition(unit, node));
            String path = Paths.get(unit.getSourceFile().toUri()).toString().replace('\\', '/');
            return path.substring(path.indexOf("src/main/java/")) + ":" + line;
        }
    }
}

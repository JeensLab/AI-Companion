import com.sun.source.tree.CaseLabelTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConstantCaseLabelTree;
import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

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
 * 2 means the check itself could not run.
 *
 * <p>Method: the sources are only parsed, never attributed. Attribution needs the Minecraft classpath, and without it
 * javac fails on some switch expressions. The enum behind a switch is found from its case labels instead: the labels
 * must all be constants of one enum declared under dev.aicompanion. Labels that fit no project enum belong to a
 * Minecraft enum, a string or a number and are skipped. Labels that fit several project enums are narrowed by a
 * qualified label ({@code Phase.CRAFT}) or by the type of the selector variable when it is declared in the same file;
 * if that still leaves several, the switch is reported as ambiguous so that nothing passes unchecked.
 */
public final class SwitchCheck {
    private static final String ROOT = "src/main/java/dev/aicompanion";

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(ROOT);
        if (!Files.isDirectory(root)) {
            System.err.println("switch-check: " + ROOT + " not found, run from the repository root");
            System.exit(2);
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
            System.exit(2);
        }
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromPaths(sources);
        JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostic -> { }, List.of("-proc:none"), null, units);
        List<CompilationUnitTree> parsed = new ArrayList<>();
        task.parse().forEach(parsed::add);

        Map<String, List<String>> enums = new LinkedHashMap<>();
        for (CompilationUnitTree unit : parsed) {
            new EnumCollector(unit, enums).scan(unit, null);
        }
        Checker checker = new Checker(Trees.instance(task).getSourcePositions(), enums);
        for (CompilationUnitTree unit : parsed) {
            checker.unit = unit;
            checker.scan(unit, null);
        }
        checker.findings.forEach(System.out::println);
        System.out.println("switch-check: " + enums.size() + " project enums, " + checker.checked + " switches checked, "
                + checker.findings.size() + " findings");
        System.exit(checker.findings.isEmpty() ? 0 : 1);
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
                    if (member instanceof VariableTree variable && variable.getType().toString().equals(node.getSimpleName().toString())
                            && variable.getInitializer() instanceof com.sun.source.tree.NewClassTree) {
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

    private static final class Checker extends TreeScanner<Void, Void> {
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
            if (!isEnumShaped || listed.isEmpty()) {
                return;
            }
            List<String> candidates = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : enums.entrySet()) {
                if (entry.getValue().containsAll(listed) && qualifiersFit(entry.getKey(), qualifiers)) {
                    candidates.add(entry.getKey());
                }
            }
            if (candidates.size() > 1) {
                candidates = narrowBySelector(selector, candidates);
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

        /** A selector that is a plain name or a field read is matched to a declaration of that name in the same file. */
        private List<String> narrowBySelector(ExpressionTree selector, List<String> candidates) {
            String name = selectorName(selector);
            if (name == null) {
                return candidates;
            }
            Set<String> declaredTypes = new LinkedHashSet<>();
            new TreeScanner<Void, Void>() {
                @Override
                public Void visitVariable(VariableTree variable, Void unused) {
                    if (variable.getName().contentEquals(name)) {
                        declaredTypes.add(variable.getType().toString());
                    }
                    return super.visitVariable(variable, unused);
                }
            }.scan(unit, null);
            List<String> narrowed = new ArrayList<>();
            for (String candidate : candidates) {
                for (String type : declaredTypes) {
                    if (candidate.equals(type) || candidate.endsWith("." + type)) {
                        narrowed.add(candidate);
                        break;
                    }
                }
            }
            return narrowed.isEmpty() ? candidates : narrowed;
        }

        private static String selectorName(ExpressionTree selector) {
            Tree tree = selector;
            while (tree instanceof com.sun.source.tree.ParenthesizedTree parenthesized) {
                tree = parenthesized.getExpression();
            }
            if (tree instanceof IdentifierTree identifier) {
                return identifier.getName().toString();
            }
            if (tree instanceof MemberSelectTree select) {
                return select.getIdentifier().toString();
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

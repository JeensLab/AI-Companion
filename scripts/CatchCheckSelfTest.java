import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Self-test of scripts/CatchCheck.java [or the copy given as argument]: runs it on a temp tree. Exit 0 when it behaves. */
public class CatchCheckSelfTest {
    static final String SOURCE = """
            package dev.aicompanion;
            class T {
                void tick() { try { a(); } catch (Throwable t) { } }
                void tick(int n) { try { a(); } catch (RuntimeException e) { } }
                void tick(String s, int n) { try { a(); } catch (Throwable t) { throw t; } }
                void tickLog() { try { a(); } catch (Error e) { LOGGER.error("x"); } }
                void two() { try { a(); } catch (Exception e) { } try { a(); } catch (Exception e) { } }
                void three() { try { a(); } catch (Exception e) { } }
                void run() {
                    Object o = new Object() {
                        void tick() { try { a(); } catch (Error e) { } }
                        void run() { try { a(); } catch (Exception e) { LOGGER.warn("x"); } }
                    };
                    try { a(); } catch (IOException | RuntimeException e) { b(); }
                    Runnable r = () -> { try { a(); } catch (Exception e) { throw new IllegalStateException(e); } };
                }
                static { try { a(); } catch (Exception e) { } }
                void ok() { try { a(); } catch (NumberFormatException e) { return; } }
            }
            """;
    static final String P = "src/main/java/dev/aicompanion/T.java";
    static final String K = P + ":T:tick/0:1";
    static final String BASELINE = "# test\n" + P + ":T:tick/1:1 empty-catch,generic-catch,tick-swallows-error r\n"
            + K + " empty-catch,generic-catch r\n" + P + ":T:gone/0:1 generic-catch r\n" + P + ":T:two/0:2 empty-catch,generic-catch r\n";
    static final List<String> BAD_BASELINES = List.of(K + " bogus-rule r", K + " empty-catch,,generic-catch r",
            K + " empty-catch, generic-catch r", K + " empty-catch r\n" + K + " generic-catch r");

    public static void main(String[] args) throws Exception {
        String tool = args.length > 0 ? args[0] : "scripts/CatchCheck.java";
        Path tree = Files.createTempDirectory("catch-check-selftest");
        int exit;
        try {
            Files.createDirectories(tree.resolve("src/main/java/dev/aicompanion"));
            Files.createDirectories(tree.resolve("tools"));
            Files.writeString(tree.resolve(P), SOURCE);
            for (String bad : BAD_BASELINES) {
                Files.writeString(tree.resolve("tools/catch-baseline.txt"), "# test\n" + bad + "\n");
                run(tool, tree, 2);
            }
            Files.writeString(tree.resolve("tools/catch-baseline.txt"), BASELINE);
            String out = run(tool, tree, 1);
            Set<String> actual = new TreeSet<>();
            Matcher m = Pattern.compile("^\\S+ ([a-z-]+): .*\\[(.+)]$", Pattern.MULTILINE).matcher(out);
            while (m.find()) actual.add(m.group(1) + " " + m.group(2));
            String anon = P + ":T.<anon@run/0>:tick/0:1", clinit = P + ":T:<clinit>:1";
            Set<String> expected = new TreeSet<>(List.of("tick-swallows-error " + K, "tick-swallows-error " + P + ":T:tickLog/0:1",
                    "empty-catch " + anon, "generic-catch " + anon, "tick-swallows-error " + anon,
                    "empty-catch " + clinit, "generic-catch " + clinit, "generic-catch " + P + ":T:run/0:1",
                    "empty-catch " + P + ":T:two/0:1", "generic-catch " + P + ":T:two/0:1",
                    "empty-catch " + P + ":T:three/0:1", "generic-catch " + P + ":T:three/0:1",
                    "baseline-stale " + P + ":T:gone/0:1", "baseline-stale " + P + ":T:tick/1:1"));
            boolean ok = actual.equals(expected) && out.contains("catch-check: 13 catches, 14 findings")
                    && out.contains("tools/catch-baseline.txt:4 baseline-stale");
            Files.delete(tree.resolve(P));
            run(tool, tree, 2);
            System.out.println(ok ? "selftest: ok" : "selftest: FAILED\nexpected " + expected + "\nactual   " + actual + "\n" + out);
            exit = ok ? 0 : 1;
        } finally {
            try (var walk = Files.walk(tree)) { walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); }
        }
        System.exit(exit);
    }

    static String run(String tool, Path tree, int expectedExit) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process p = new ProcessBuilder(java, tool, tree.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        if (code != expectedExit) throw new IllegalStateException("exit " + code + " instead of " + expectedExit + "\n" + out);
        return out;
    }
}

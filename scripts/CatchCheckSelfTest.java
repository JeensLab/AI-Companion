import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Self-test of scripts/CatchCheck.java: runs it on a temp tree and compares the "rule key" pairs. Exit 0 when it behaves. */
public class CatchCheckSelfTest {
    static final String SOURCE = """
            package dev.aicompanion;
            class T {
                void tick() { try { a(); } catch (Throwable t) { } }
                void tick(int n) { try { a(); } catch (RuntimeException e) { } }
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
    static final String BASELINE = "# test\n"
            + P + ":T:tick/1:1 empty-catch,generic-catch,tick-swallows-error r\n"
            + P + ":T:tick/0:1 empty-catch,generic-catch r\n"
            + P + ":T:gone/0:1 generic-catch r\n";

    public static void main(String[] args) throws Exception {
        Path tree = Files.createTempDirectory("catch-check-selftest");
        Files.createDirectories(tree.resolve("src/main/java/dev/aicompanion"));
        Files.createDirectories(tree.resolve("tools"));
        Files.writeString(tree.resolve(P), SOURCE);
        Files.writeString(tree.resolve("tools/catch-baseline.txt"), BASELINE);
        String out = run(tree, 1);
        Set<String> actual = new TreeSet<>();
        Matcher m = Pattern.compile("^\\S+ ([a-z-]+): .*\\[(.+)]$", Pattern.MULTILINE).matcher(out);
        while (m.find()) actual.add(m.group(1) + " " + m.group(2));
        Set<String> expected = new TreeSet<>(List.of(
                "tick-swallows-error " + P + ":T:tick/0:1",
                "empty-catch " + P + ":T.<anon@run/0>:tick/0:1", "generic-catch " + P + ":T.<anon@run/0>:tick/0:1",
                "tick-swallows-error " + P + ":T.<anon@run/0>:tick/0:1",
                "generic-catch " + P + ":T:run/0:1",
                "empty-catch " + P + ":T:<clinit>:1", "generic-catch " + P + ":T:<clinit>:1",
                "baseline-stale " + P + ":T:gone/0:1", "baseline-stale " + P + ":T:tick/1:1"));
        boolean ok = actual.equals(expected) && out.contains("catch-check: 8 catches, 9 findings");
        Files.delete(tree.resolve(P));
        run(tree, 2);
        try (var walk = Files.walk(tree)) { walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); }
        System.out.println(ok ? "selftest: ok" : "selftest: FAILED\nexpected " + expected + "\nactual   " + actual + "\n" + out);
        System.exit(ok ? 0 : 1);
    }

    static String run(Path tree, int expectedExit) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process p = new ProcessBuilder(java, "scripts/CatchCheck.java", tree.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        if (code != expectedExit) throw new IllegalStateException("exit " + code + " instead of " + expectedExit + "\n" + out);
        return out;
    }
}

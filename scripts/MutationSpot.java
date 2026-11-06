import com.sun.source.util.JavacTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Spot mutation testing: breaks one real rule in the sources (tools/mutants.txt), runs only the GameTests that own that rule,
 * and expects at least one of them to fail. A mutant that every selected test survives is a rule nobody checks.
 *
 * <p>Usage, from the repository root (the wrappers scripts/mutation-spot.sh and .ps1 call this):
 * <pre>
 *   java scripts/MutationSpot.java --list
 *   java scripts/MutationSpot.java --dry-run --all     # apply, check the syntax, show the diff, restore; no Gradle
 *   java scripts/MutationSpot.java --one &lt;id&gt;
 *   java scripts/MutationSpot.java --all
 * </pre>
 * The working tree must be clean before a run so that {@code git checkout -- <file>} puts every byte back; the file is restored
 * after each mutant, on failure and on Ctrl-C, and the tree is checked clean again. Exit 1 when a mutant survived or is broken.
 */
public final class MutationSpot {
    private static final String TABLE = "tools/mutants.txt";
    private static final String SEPARATOR = " \\| ";
    private static final long GRADLE_TIMEOUT_MINUTES = 40;
    private static final Pattern FAILED_TEST = Pattern.compile("LogTestReporter[^\\r\\n]*?\\]: ([^\\r\\n]+) failed at");

    private record Mutant(String id, String file, String from, String to, List<String> methods, String why, String line) { }

    private enum Verdict { KILLED, SURVIVED, BROKEN, ERROR }

    private static Path root;

    public static void main(String[] args) throws Exception {
        root = Path.of(args.length > 0 && args[0].equals("--root") ? args[1] : ".").toAbsolutePath().normalize();
        List<String> rest = new ArrayList<>(List.of(args));
        if (rest.size() > 1 && rest.get(0).equals("--root")) { rest.remove(0); rest.remove(0); }
        boolean dryRun = rest.remove("--dry-run");
        boolean list = rest.remove("--list");
        boolean all = rest.remove("--all");
        String one = null;
        int oneAt = rest.indexOf("--one");
        if (oneAt >= 0 && oneAt + 1 < rest.size()) { one = rest.get(oneAt + 1); rest.remove(oneAt + 1); rest.remove(oneAt); }
        if (!rest.isEmpty() || (!list && !all && one == null) || (all && one != null)) {
            System.err.println("usage: mutation-spot (--list | [--dry-run] --all | [--dry-run] --one <id>)");
            System.exit(2);
        }
        List<Mutant> table = readTable();
        if (list) {
            for (Mutant mutant : table) System.out.println(mutant.line);
            return;
        }
        List<Mutant> chosen = new ArrayList<>();
        for (Mutant mutant : table) if (all || mutant.id.equals(one)) chosen.add(mutant);
        if (chosen.isEmpty()) { System.err.println("no such mutant: " + one); System.exit(2); }
        if (!git("status", "--porcelain").isBlank()) {
            System.err.println("the working tree is not clean; commit or stash first so the mutated file can be restored:\n" + git("status", "--porcelain"));
            System.exit(2);
        }
        System.exit(runAll(chosen, dryRun) ? 0 : 1);
    }

    private static boolean runAll(List<Mutant> chosen, boolean dryRun) throws Exception {
        int survived = 0, killed = 0, other = 0;
        for (Mutant mutant : chosen) {
            Verdict verdict = runOne(mutant, dryRun);
            if (verdict == Verdict.KILLED) killed++;
            else if (verdict == Verdict.SURVIVED) survived++;
            else other++;
        }
        System.out.println("mutation-spot: " + chosen.size() + " mutants, " + killed + " killed, " + survived + " survived, " + other + " broken or failed"
                + (dryRun ? " (dry run: nothing was executed)" : ""));
        return survived == 0 && other == 0;
    }

    private static Verdict runOne(Mutant mutant, boolean dryRun) throws Exception {
        Path file = root.resolve(mutant.file);
        String source = Files.readString(file, StandardCharsets.UTF_8);
        int first = source.indexOf(mutant.from);
        if (first < 0 || source.indexOf(mutant.from, first + 1) >= 0) {
            System.out.println("BROKEN " + mutant.id + " (from-text must occur exactly once in " + mutant.file + ")");
            return Verdict.BROKEN;
        }
        Thread restore = new Thread(() -> restoreQuietly(mutant.file));
        Runtime.getRuntime().addShutdownHook(restore);
        try {
            String mutated = source.substring(0, first) + mutant.to + source.substring(first + mutant.from.length());
            Files.writeString(file, mutated, StandardCharsets.UTF_8);
            String syntax = syntaxErrors(file);
            if (syntax != null) {
                System.out.println("BROKEN " + mutant.id + " (the mutated file does not parse: " + syntax + ")");
                return Verdict.BROKEN;
            }
            if (dryRun) {
                System.out.println("APPLIED " + mutant.id + " in " + mutant.file);
                System.out.print(git("diff", "-U0", "--", mutant.file));
                return Verdict.KILLED;
            }
            return execute(mutant);
        } finally {
            git("checkout", "--", mutant.file);
            Runtime.getRuntime().removeShutdownHook(restore);
            String dirty = git("status", "--porcelain");
            if (!dirty.isBlank()) {
                System.err.println("the tree is not clean after " + mutant.id + ", stopping:\n" + dirty);
                System.exit(3);
            }
        }
    }

    private static Verdict execute(Mutant mutant) throws Exception {
        Path logs = root.resolve("build/diagnostics");
        Files.createDirectories(logs);
        Path log = logs.resolve("mutation-" + mutant.id + ".log");
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        List<String> command = List.of(root.resolve(windows ? "gradlew.bat" : "gradlew").toString(), "runGameTestServer",
                "-PgameTestMethods=" + String.join(",", mutant.methods), "-PgameTestDirectory=run/mutation", "--console=plain");
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(log.toFile())).start();
        boolean finished = process.waitFor(GRADLE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            System.out.println("KILLED " + mutant.id + " by timeout (no result in " + GRADLE_TIMEOUT_MINUTES + " minutes, see " + log + ")");
            return Verdict.KILLED;
        }
        String output = Files.readString(log, StandardCharsets.UTF_8);
        int exit = process.exitValue();
        if (output.contains("Execution failed for task ':compileJava'")) {
            System.out.println("BROKEN " + mutant.id + " (the mutant does not compile, see " + log + ")");
            return Verdict.BROKEN;
        }
        String allPassed = "All " + mutant.methods.size() + " required tests passed";
        if (exit == 0 && output.contains(allPassed)) {
            System.out.println("SURVIVED " + mutant.id + " (" + String.join(",", mutant.methods) + " all passed under the mutant)");
            return Verdict.SURVIVED;
        }
        Matcher failed = FAILED_TEST.matcher(output);
        if (failed.find() || output.contains("required tests failed")) {
            System.out.println("KILLED " + mutant.id + " by " + (failed.find(0) ? failed.group(1) : "a failed test"));
            return Verdict.KILLED;
        }
        System.out.println("ERROR " + mutant.id + " (Gradle exit " + exit + " without a test report, see " + log + ")");
        return Verdict.ERROR;
    }

    // Parse only: a mutant that no longer parses would look like a kill, so it is reported as broken instead.
    private static String syntaxErrors(Path file) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null,
                    manager.getJavaFileObjects(file));
            task.parse();
        }
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) return "line " + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(null);
        }
        return null;
    }

    private static List<Mutant> readTable() throws IOException {
        List<Mutant> table = new ArrayList<>();
        int number = 0;
        for (String raw : Files.readAllLines(root.resolve(TABLE), StandardCharsets.UTF_8)) {
            number++;
            if (raw.isBlank() || raw.startsWith("#")) continue;
            String[] fields = raw.split(SEPARATOR, -1);
            if (fields.length != 6) throw new IllegalStateException(TABLE + ":" + number + ": expected 6 fields separated by ' | ', found " + fields.length);
            List<String> methods = List.of(fields[4].trim().split("\\s*,\\s*"));
            if (fields[2].isEmpty() || fields[2].equals(fields[3])) throw new IllegalStateException(TABLE + ":" + number + ": from-text is empty or equals to-text");
            table.add(new Mutant(fields[0].trim(), fields[1].trim(), fields[2], fields[3], methods, fields[5].trim(), raw));
        }
        return table;
    }

    private static void restoreQuietly(String file) {
        try {
            git("checkout", "--", file);
        } catch (Exception ignored) {
            // The next run refuses to start on a dirty tree and names the file.
        }
    }

    private static String git(String... arguments) throws IOException {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (process.waitFor() != 0) throw new IOException("git " + String.join(" ", arguments) + " failed: " + output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        return output;
    }
}

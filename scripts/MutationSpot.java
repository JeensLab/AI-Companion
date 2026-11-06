import com.sun.source.util.JavacTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
 * and expects one of them to fail. A mutant that every selected test survives is a rule nobody checks.
 *
 * <p>Usage, from the repository root (the wrappers scripts/mutation-spot.sh and .ps1 call this):
 * <pre>
 *   java scripts/MutationSpot.java --list
 *   java scripts/MutationSpot.java --dry-run --all     # apply, check the syntax, show the diff, restore; no Gradle
 *   java scripts/MutationSpot.java --one &lt;id&gt; [--timeout-min 20]
 *   java scripts/MutationSpot.java --all [--timeout-min 20]
 * </pre>
 * Before the first mutant the selected tests run once on the unmutated tree; a failure there stops everything, so a failure
 * under a mutant can only mean the mutant. KILLED needs the failing test to be one of the listed methods.
 *
 * <p>The working tree must be clean, untracked files included (in practice a separate worktree), so that
 * {@code git checkout -- <file>} puts every byte of the one mutated file back; it is restored after each mutant, on failure and
 * on Ctrl-C. Exit codes: 0 all killed; 1 a mutant survived, is broken, timed out or ended with an unexpected failure;
 * 2 usage error, unreadable table, failing git, dirty tree or a red baseline; 3 the tree was still dirty after a restore.
 */
public final class MutationSpot {
    private static final String TABLE = "tools/mutants.txt";
    private static final String SEPARATOR = " \\| ";
    private static final int DEFAULT_TIMEOUT_MINUTES = 20;
    private static final Pattern FAILED_TEST = Pattern.compile("LogTestReporter[^\\r\\n]*?\\]: ([^\\r\\n]+) failed at");

    private record Mutant(String id, String file, String from, String to, List<String> methods, String why, String line) { }

    private record GradleRun(boolean timedOut, int exit, String output, Path log) { }

    private enum Verdict { KILLED, SURVIVED, BROKEN, TIMEOUT, ERROR }

    private static Path root;
    private static int timeoutMinutes = DEFAULT_TIMEOUT_MINUTES;
    private static volatile Process child;

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IOException | RuntimeException e) {
            System.err.println("mutation-spot: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
            System.exit(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("mutation-spot: interrupted");
            System.exit(2);
        }
    }

    private static void run(String[] args) throws IOException, InterruptedException {
        List<String> rest = new ArrayList<>(List.of(args));
        root = Path.of(".").toAbsolutePath().normalize();
        String rootArg = take(rest, "--root");
        if (rootArg != null) root = Path.of(rootArg).toAbsolutePath().normalize();
        String timeoutArg = take(rest, "--timeout-min");
        if (timeoutArg != null) timeoutMinutes = timeoutArg.matches("\\d{1,4}") ? Integer.parseInt(timeoutArg) : 0;
        boolean dryRun = rest.remove("--dry-run");
        boolean list = rest.remove("--list");
        boolean all = rest.remove("--all");
        String one = take(rest, "--one");
        if (!rest.isEmpty() || timeoutMinutes < 1 || (!list && !all && one == null) || (all && one != null)) {
            System.err.println("usage: mutation-spot (--list | [--dry-run] --all | [--dry-run] --one <id>) [--timeout-min N]");
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
            System.err.println("the working tree is not clean (untracked files count); use a clean worktree so the mutated file can be restored:\n"
                    + git("status", "--porcelain"));
            System.exit(2);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(MutationSpot::stopChild));
        if (!dryRun && !baselineIsGreen(chosen)) System.exit(2);
        System.exit(runAll(chosen, dryRun) ? 0 : 1);
    }

    private static String take(List<String> args, String flag) {
        int at = args.indexOf(flag);
        if (at < 0 || at + 1 >= args.size()) return null;
        String value = args.get(at + 1);
        args.remove(at + 1);
        args.remove(at);
        return value;
    }

    private static boolean runAll(List<Mutant> chosen, boolean dryRun) throws IOException, InterruptedException {
        int survived = 0, killed = 0, other = 0;
        for (Mutant mutant : chosen) {
            Verdict verdict = runOne(mutant, dryRun);
            if (verdict == Verdict.KILLED) killed++;
            else if (verdict == Verdict.SURVIVED) survived++;
            else other++;
        }
        System.out.println("mutation-spot: " + chosen.size() + " mutants, " + killed + (dryRun ? " applied and restored, " : " killed, ")
                + survived + " survived, " + other + " broken, timed out or failed" + (dryRun ? " (dry run: Gradle was not run)" : ""));
        return survived == 0 && other == 0;
    }

    // The selected tests, once, on the unmutated tree: without it a red test would look like a kill.
    private static boolean baselineIsGreen(List<Mutant> chosen) throws IOException, InterruptedException {
        Set<String> methods = new LinkedHashSet<>();
        for (Mutant mutant : chosen) methods.addAll(mutant.methods);
        GradleRun run = gradle(new ArrayList<>(methods), "mutation-baseline");
        if (run.timedOut) {
            System.out.println("ERROR baseline (no result in " + timeoutMinutes + " minutes, see " + run.log + ")");
            return false;
        }
        if (run.exit == 0 && run.output.contains("All " + methods.size() + " required tests passed")) {
            System.out.println("baseline green: " + methods.size() + " tests on the unmutated tree");
            return true;
        }
        List<String> failed = failedTests(run.output);
        System.out.println("ERROR baseline " + (failed.isEmpty() ? "(Gradle exit " + run.exit + " without a test report, see " + run.log + ")" : String.join(", ", failed)));
        return false;
    }

    private static Verdict runOne(Mutant mutant, boolean dryRun) throws IOException, InterruptedException {
        Path file = root.resolve(mutant.file);
        String source = Files.readString(file, StandardCharsets.UTF_8);
        int first = source.indexOf(mutant.from);
        if (first < 0 || source.indexOf(mutant.from, first + 1) >= 0) {
            System.out.println("BROKEN " + mutant.id + " (from-text must occur exactly once in " + mutant.file + ")");
            return Verdict.BROKEN;
        }
        // On Ctrl-C the Gradle child must stop before the file is put back, or it would go on testing a half-restored tree.
        Thread restore = new Thread(() -> { stopChild(); restoreQuietly(mutant.file); });
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

    private static Verdict execute(Mutant mutant) throws IOException, InterruptedException {
        GradleRun run = gradle(mutant.methods, "mutation-" + mutant.id);
        if (run.timedOut) {
            System.out.println("TIMEOUT " + mutant.id + " (no result in " + timeoutMinutes + " minutes, not counted as a kill, see " + run.log + ")");
            return Verdict.TIMEOUT;
        }
        if (run.output.contains("Execution failed for task ':compileJava'")) {
            System.out.println("BROKEN " + mutant.id + " (the mutant does not compile, see " + run.log + ")");
            return Verdict.BROKEN;
        }
        if (run.exit == 0 && run.output.contains("All " + mutant.methods.size() + " required tests passed")) {
            System.out.println("SURVIVED " + mutant.id + " (" + String.join(",", mutant.methods) + " all passed under the mutant)");
            return Verdict.SURVIVED;
        }
        List<String> failed = failedTests(run.output);
        List<String> expected = new ArrayList<>();
        List<String> unexpected = new ArrayList<>();
        for (String name : failed) {
            String owner = mutant.methods.stream().filter(method -> name.toLowerCase(Locale.ROOT).equals(method.toLowerCase(Locale.ROOT))).findFirst().orElse(null);
            if (owner == null) unexpected.add(name);
            else expected.add(owner);
        }
        if (!expected.isEmpty() && unexpected.isEmpty()) {
            System.out.println("KILLED " + mutant.id + " by " + expected.get(0));
            return Verdict.KILLED;
        }
        String detail = failed.isEmpty() ? "Gradle exit " + run.exit + " without a failed test in the report" : "unexpected failing tests: " + String.join(", ", unexpected);
        System.out.println("ERROR " + mutant.id + " (" + detail + ", see " + run.log + ")");
        return Verdict.ERROR;
    }

    /**
     * One focused GameTest run. Gradle runs with {@code --no-daemon}: a single-use daemon dies with its client, and no other
     * worktree's daemon is touched. On a timeout, or on Ctrl-C, the client process tree is destroyed; a game-test JVM that Gradle
     * forked and detached may still survive that hard kill, so look for a stray java process if the machine stays busy.
     */
    private static GradleRun gradle(List<String> methods, String logName) throws IOException, InterruptedException {
        Path logs = root.resolve("build/diagnostics");
        Files.createDirectories(logs);
        Path log = logs.resolve(logName + ".log");
        List<String> command = List.of(gradlew(), "runGameTestServer", "-PgameTestMethods=" + String.join(",", methods),
                "-PgameTestDirectory=run/mutation", "--console=plain", "--no-daemon");
        child = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(log.toFile())).start();
        boolean finished = child.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!finished) stopChild();
        int exit = finished ? child.exitValue() : -1;
        child = null;
        return new GradleRun(!finished, exit, new String(Files.readAllBytes(log), StandardCharsets.UTF_8), log);
    }

    private static void stopChild() {
        Process process = child;
        if (process == null || !process.isAlive()) return;
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static String gradlew() {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        return root.resolve(windows ? "gradlew.bat" : "gradlew").toString();
    }

    private static List<String> failedTests(String output) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = FAILED_TEST.matcher(output);
        while (matcher.find()) names.add(matcher.group(1).trim());
        return new ArrayList<>(names);
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

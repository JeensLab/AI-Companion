import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Compares the PMD complexity report with the baseline of violations that already existed, so the build fails on a new one
 * and the baseline can only shrink. A key is {@code path:rule:Class.method} (or {@code path:rule:Class} for a violation of a
 * whole class), never a line number, so an edit elsewhere in the file does not change it. Overloads of one method get a
 * {@code #2}, {@code #3} suffix in source order.
 *
 * <p>Usage, from the repository root, after {@code ./gradlew pmdMain}:
 * <pre>
 *   java scripts/PmdCheck.java                  # exit 0 clean, 1 new violation or stale entry, 2 report missing or broken
 *   java scripts/PmdCheck.java --print-keys     # the keys of the current report, sorted, for rebuilding the baseline
 *   java scripts/PmdCheck.java --selftest
 *   options: --report build/reports/pmd/main.xml  --baseline tools/pmd-baseline.txt
 * </pre>
 */
public final class PmdCheck {
    private static final String DEFAULT_REPORT = "build/reports/pmd/main.xml";
    private static final String DEFAULT_BASELINE = "tools/pmd-baseline.txt";

    public static void main(String[] args) throws Exception {
        String report = DEFAULT_REPORT;
        String baseline = DEFAULT_BASELINE;
        boolean printKeys = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--selftest" -> {
                    System.exit(selfTest());
                }
                case "--print-keys" -> printKeys = true;
                case "--report", "--baseline" -> {
                    if (i + 1 >= args.length) {
                        System.err.println(args[i] + " needs a value");
                        System.exit(2);
                    }
                    if (args[i].equals("--report")) {
                        report = args[++i];
                    } else {
                        baseline = args[++i];
                    }
                }
                default -> {
                    System.err.println("unknown argument: " + args[i]);
                    System.exit(2);
                }
            }
        }
        System.exit(run(Paths.get("").toAbsolutePath(), Paths.get(report), Paths.get(baseline), printKeys, System.out, System.err));
    }

    static int run(Path root, Path report, Path baseline, boolean printKeys, PrintStream out, PrintStream err) {
        Map<String, String> found;
        try {
            found = readFindings(root, report);
        } catch (Exception e) {
            err.println("pmd-check: cannot read " + report + ": " + e.getMessage());
            return 2;
        }
        if (printKeys) {
            new TreeSet<>(found.keySet()).forEach(out::println);
            return 0;
        }
        Set<String> allowed;
        try {
            allowed = readBaseline(baseline);
        } catch (Exception e) {
            err.println("pmd-check: cannot read " + baseline + ": " + e.getMessage());
            return 2;
        }
        Set<String> current = found.keySet();
        List<String> added = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        int baselined = 0;
        for (String key : current) {
            if (allowed.contains(key)) {
                baselined++;
            } else {
                added.add(key);
            }
        }
        for (String key : allowed) {
            if (!current.contains(key)) {
                stale.add(key);
            }
        }
        Collections.sort(added);
        Collections.sort(stale);
        added.forEach(key -> out.println("new: " + found.get(key) + " [" + key + "]"));
        stale.forEach(key -> out.println("baseline-stale: " + key));
        out.println("pmd-check: " + current.size() + " violations, " + baselined + " baselined, " + added.size() + " new, "
            + stale.size() + " stale");
        String shown = baseline.toString().replace('\\', '/');
        if (!added.isEmpty()) {
            out.println("Fix a new violation by splitting the decision into a named method; do not add it to " + shown + ".");
        }
        if (!stale.isEmpty()) {
            out.println("Delete each baseline-stale line from " + shown + ": the baseline only shrinks.");
        }
        return added.isEmpty() && stale.isEmpty() ? 0 : 1;
    }

    /** Key to a human line {@code path:line Rule: message}, in report order. */
    private static Map<String, String> readFindings(Path root, Path report) throws Exception {
        if (!Files.isRegularFile(report)) {
            throw new IllegalStateException("no report, run ./gradlew pmdMain first");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override
            public void fatalError(SAXParseException e) throws SAXException {
                throw e;
            }
        });
        Document document = builder.parse(report.toFile());
        // A file PMD could not analyse would silently hide its violations.
        for (String problem : new String[] {"error", "configerror"}) {
            NodeList problems = document.getElementsByTagName(problem);
            if (problems.getLength() > 0) {
                Element first = (Element) problems.item(0);
                throw new IllegalStateException("PMD reported " + problems.getLength() + " <" + problem + "> entries, first: "
                    + first.getAttribute("filename") + " " + first.getAttribute("msg"));
            }
        }
        Map<String, String> findings = new LinkedHashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        NodeList files = document.getElementsByTagName("file");
        for (int i = 0; i < files.getLength(); i++) {
            Element file = (Element) files.item(i);
            String path = relative(root, file.getAttribute("name"));
            NodeList violations = file.getElementsByTagName("violation");
            List<Element> ordered = new ArrayList<>();
            for (int j = 0; j < violations.getLength(); j++) {
                ordered.add((Element) violations.item(j));
            }
            ordered.sort((a, b) -> Integer.compare(line(a), line(b)));
            for (Element violation : ordered) {
                String owner = violation.getAttribute("class");
                String method = violation.getAttribute("method");
                String rule = violation.getAttribute("rule");
                String key = path + ":" + rule + ":" + (method.isEmpty() ? owner : owner + "." + method);
                int count = seen.merge(key, 1, Integer::sum);
                String message = violation.getTextContent().trim().replaceAll("\s+", " ");
                findings.put(count == 1 ? key : key + "#" + count, path + ":" + line(violation) + " " + rule + ": " + message);
            }
        }
        return findings;
    }

    private static int line(Element violation) {
        return Integer.parseInt(violation.getAttribute("beginline"));
    }

    private static String relative(Path root, String name) {
        Path file = Paths.get(name).toAbsolutePath().normalize();
        Path shown = file.startsWith(root) ? root.relativize(file) : file;
        return shown.toString().replace('\\', '/');
    }

    private static Set<String> readBaseline(Path baseline) throws Exception {
        Set<String> keys = new LinkedHashSet<>();
        for (String line : Files.readAllLines(baseline, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                keys.add(trimmed);
            }
        }
        return keys;
    }

    private static int selfTest() throws Exception {
        Path dir = Files.createTempDirectory("pmd-check-selftest");
        try {
            return selfTestIn(dir);
        } finally {
            try (Stream<Path> tree = Files.walk(dir)) {
                tree.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    private static int selfTestIn(Path dir) throws Exception {
        Path report = dir.resolve("main.xml");
        Path baseline = dir.resolve("baseline.txt");
        String file = dir.resolve("src/A.java").toString();
        String xml = "<?xml version=\"1.0\"?><pmd><file name=\"" + file + "\">"
            + "<violation beginline=\"9\" rule=\"CyclomaticComplexity\" class=\"A\" method=\"f\">m</violation>"
            + "<violation beginline=\"30\" rule=\"CyclomaticComplexity\" class=\"A\" method=\"f\">m</violation>"
            + "<violation beginline=\"1\" rule=\"TooManyMethods\" class=\"A\">m</violation></file></pmd>";
        String f1 = "src/A.java:CyclomaticComplexity:A.f";
        String f2 = f1 + "#2";
        String whole = "src/A.java:TooManyMethods:A";
        PrintStream quiet = new PrintStream(java.io.OutputStream.nullOutputStream());
        int failures = 0;
        Files.writeString(report, xml);
        Files.writeString(baseline, "# header\n\n" + f1 + "\n" + f2 + "\n" + whole + "\n");
        failures += expect("clean", 0, run(dir, report, baseline, false, quiet, quiet));
        Files.writeString(baseline, f1 + "\n" + whole + "\n");
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        failures += expect("new violation", 1, run(dir, report, baseline, false, new PrintStream(captured), quiet));
        if (!captured.toString().contains("new: src/A.java:30 CyclomaticComplexity: m [" + f2 + "]")) {
            System.out.println("selftest new violation: no line and message in " + captured);
            failures++;
        }
        Files.writeString(baseline, f1 + "\n" + f2 + "\n" + whole + "\nsrc/A.java:NPathComplexity:A.gone\n");
        failures += expect("stale entry", 1, run(dir, report, baseline, false, quiet, quiet));
        failures += expect("missing report", 2, run(dir, dir.resolve("none.xml"), baseline, false, quiet, quiet));
        failures += expect("missing baseline", 2, run(dir, report, dir.resolve("none.txt"), false, quiet, quiet));
        Files.writeString(report, "<pmd><file name=\"" + file + "\"/><error filename=\"x\" msg=\"boom\"/></pmd>");
        failures += expect("PMD error entry", 2, run(dir, report, baseline, false, quiet, quiet));
        Files.writeString(report, "<pmd>");
        failures += expect("broken xml", 2, run(dir, report, baseline, false, quiet, quiet));
        System.out.println(failures == 0 ? "selftest passed" : "selftest failed: " + failures);
        return failures == 0 ? 0 : 1;
    }

    private static int expect(String name, int expected, int actual) {
        if (expected != actual) {
            System.out.println("selftest " + name + ": expected exit " + expected + ", got " + actual);
            return 1;
        }
        return 0;
    }
}

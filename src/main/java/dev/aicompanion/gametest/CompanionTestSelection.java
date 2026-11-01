package dev.aicompanion.gametest;

import com.mojang.logging.LogUtils;
import dev.aicompanion.AICompanionMod;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

/**
 * Narrows a GameTest run from system properties, so a focused run or a shard leaves the sources alone.
 * Tests are spread by batch because a batch shares its before/after hooks; only the default batch is split per test.
 */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID)
public final class CompanionTestSelection {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_BATCH = "defaultBatch";
    private static final List<String> TEST_PACKAGES = List.of("dev.aicompanion.gametest.", "dev.aicompanion.entity.");

    private CompanionTestSelection() {
    }

    @SubscribeEvent
    public static void select(ServerAboutToStartEvent event) {
        if (!(event.getServer() instanceof GameTestServer server)) return;
        // The server copies the registry before any event fires, so its own list is the last place to narrow.
        List<TestFunction> tests = ObfuscationReflectionHelper.getPrivateValue(GameTestServer.class, server, "testFunctions");
        List<TestFunction> pool = pool(tests);
        List<TestFunction> selected = shard(pool, System.getProperty("aicompanion.gameTests.shard", ""));
        if (selected.isEmpty()) throw new IllegalStateException("The game test selection is empty");
        tests.retainAll(selected);
        cleanAfterEveryBatch(selected);
        LOGGER.info("Game test selection: pool={} selected={} pool-required={} selected-required={}",
                pool.size(), selected.size(), required(pool), required(selected));
    }

    // Batch order changes with the selection, so no batch may rely on what the previous one happened to leave.
    private static void cleanAfterEveryBatch(List<TestFunction> tests) {
        Map<String, Consumer<ServerLevel>> after = ObfuscationReflectionHelper.getPrivateValue(GameTestRegistry.class, null, "AFTER_BATCH_FUNCTIONS");
        for (TestFunction test : tests) after.putIfAbsent(test.batchName(), CompanionFixtureCleanup::clean);
    }

    private static List<TestFunction> pool(List<TestFunction> tests) {
        Set<String> known = new HashSet<>();
        for (TestFunction test : tests) known.add(test.testName());
        Set<String> wanted = new HashSet<>(methods("aicompanion.gameTests.methods", known));
        for (String name : list("aicompanion.gameTests.classes")) wanted.addAll(methodsOf(name));
        boolean everything = list("aicompanion.gameTests.classes").isEmpty() && list("aicompanion.gameTests.methods").isEmpty();
        Set<String> skipped = methods("aicompanion.gameTests.skipMethods", known);
        List<TestFunction> pool = new ArrayList<>();
        for (TestFunction test : tests)
            if ((everything || wanted.contains(test.testName())) && !skipped.contains(test.testName())) pool.add(test);
        return pool;
    }

    private static List<TestFunction> shard(List<TestFunction> pool, String shard) {
        if (shard.isBlank()) return pool;
        String[] parts = shard.split("/");
        int index = Integer.parseInt(parts[0]);
        long[] load = new long[Integer.parseInt(parts[1])];
        Map<String, Long> weights = new TreeMap<>();
        for (TestFunction test : pool) weights.merge(unit(test), (long) test.maxTicks(), Long::sum);
        // Heaviest first into the lightest shard: every process computes the same deal from the same pool.
        List<Map.Entry<String, Long>> units = new ArrayList<>(weights.entrySet());
        units.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        Set<String> mine = new HashSet<>();
        for (Map.Entry<String, Long> unit : units) {
            int lightest = 0;
            for (int i = 1; i < load.length; i++) if (load[i] < load[lightest]) lightest = i;
            load[lightest] += unit.getValue();
            if (lightest == index) mine.add(unit.getKey());
        }
        List<TestFunction> selected = new ArrayList<>();
        for (TestFunction test : pool) if (mine.contains(unit(test))) selected.add(test);
        return selected;
    }

    private static String unit(TestFunction test) {
        return test.batchName().equals(DEFAULT_BATCH) ? "test:" + test.testName() : "batch:" + test.batchName();
    }

    private static long required(List<TestFunction> tests) {
        return tests.stream().filter(TestFunction::required).count();
    }

    private static Set<String> methods(String property, Set<String> known) {
        Set<String> names = new HashSet<>();
        for (String name : list(property)) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!known.contains(lower)) throw new IllegalArgumentException("Unknown game test: " + name);
            names.add(lower);
        }
        return names;
    }

    private static Set<String> methodsOf(String simpleName) {
        for (String prefix : TEST_PACKAGES) {
            Class<?> type;
            try {
                type = Class.forName(prefix + simpleName, false, CompanionTestSelection.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                continue;
            }
            Set<String> names = new HashSet<>();
            for (Method method : type.getDeclaredMethods())
                if (method.isAnnotationPresent(GameTest.class)) names.add(method.getName().toLowerCase(Locale.ROOT));
            if (names.isEmpty()) throw new IllegalArgumentException("No game tests in class: " + simpleName);
            return names;
        }
        throw new IllegalArgumentException("Unknown game test class: " + simpleName);
    }

    private static List<String> list(String property) {
        return Arrays.stream(System.getProperty(property, "").split(",")).map(String::trim).filter(name -> !name.isEmpty()).toList();
    }
}

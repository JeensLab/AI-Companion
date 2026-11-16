package dev.aicompanion.client;

import dev.aicompanion.CompanionJokeBank;
import dev.aicompanion.CompanionJokeBank.Joke;
import dev.aicompanion.CompanionJokes;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Client-thread lifecycle; background tasks return data and never touch client state. */
final class CompanionJokeFiles {
    private static CompletableFuture<CompanionJokeBank.Bank> loading;
    private static List<Joke> custom = List.of();
    private static boolean russianPlayer, bundledRussian;
    private static String status = "Файл ещё не загружен";
    private CompanionJokeFiles() { }
    static Path path() { return FMLPaths.CONFIGDIR.get().resolve("aicompanion-jokes.txt"); }
    static boolean loading() { return loading != null; }
    static String status() { return status; }
    static void reset(boolean inWorld) {
        loading = null; custom = List.of(); russianPlayer = false; bundledRussian = false;
        if (inWorld) reload(false);
    }
    static void playerMessage(String text) { if (CompanionJokeBank.hasRussian(text)) russianPlayer = true; }
    private static boolean wantsRussian() {
        return CompanionJokeBank.allowBundled(Minecraft.getInstance().getLanguageManager().getSelected(),
                java.util.Locale.getDefault().getLanguage(), russianPlayer);
    }
    static void reload(boolean open) {
        Path file = path();
        status = open ? "Открываю файл…" : "Загружаю анекдоты…";
        loading = CompletableFuture.supplyAsync(() -> {
            try {
                if (open) {
                    CompanionJokeBank.createTemplate(file);
                    net.minecraft.Util.getPlatform().openFile(file.toFile());
                }
                return CompanionJokeBank.load(file);
            } catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
        });
    }
    static void tick(CompanionJokes jokes) {
        boolean changed = false;
        if (loading != null && loading.isDone()) {
            try {
                var bank = loading.join(); custom = bank.custom();
                var parsed = bank.parsed();
                status = "Из файла: " + custom.size() + "; пропущено: " + parsed.skipped()
                        + " (ошибки " + parsed.invalid() + ", повторы " + parsed.duplicates() + ", сверх лимита " + parsed.overflow() + ")";
                changed = true;
            } catch (java.util.concurrent.CompletionException error) {
                status = "Не удалось прочитать UTF-8 файл (до 2 МиБ). Прежний набор сохранён.";
                dev.aicompanion.AICompanionMod.LOGGER.warn("Cannot reload local joke bank: {}", error.getCause().getClass().getSimpleName());
            }
            loading = null;
        }
        boolean russian = wantsRussian();
        if (changed || bundledRussian != russian) {
            bundledRussian = russian;
            jokes.reloadBank(CompanionJokeBank.combine(russian ? CompanionJokes.BUNDLED : List.of(), custom));
        }
    }
}

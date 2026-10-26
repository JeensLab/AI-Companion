package dev.aicompanion.client;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** Retries transport failures only. The successful model response is decoded and executed once. */
public final class OpenRouterRetry {
    private static final Set<Integer> TEMPORARY = Set.of(408, 429, 500, 502, 503, 504);
    private OpenRouterRetry() { }

    public static <T> CompletableFuture<T> execute(Supplier<CompletableFuture<T>> request, ToIntFunction<T> status,
                                                   Function<T, String> retryAfter) {
        return execute(request, status, retryAfter, duration -> CompletableFuture.runAsync(() -> { },
                CompletableFuture.delayedExecutor(duration.toMillis(), TimeUnit.MILLISECONDS)));
    }

    public static <T> CompletableFuture<T> execute(Supplier<CompletableFuture<T>> request, ToIntFunction<T> status,
                                                   Function<T, String> retryAfter, Function<Duration, CompletableFuture<Void>> delay) {
        return attempt(request, status, retryAfter, delay, 0);
    }

    private static <T> CompletableFuture<T> attempt(Supplier<CompletableFuture<T>> request, ToIntFunction<T> status,
                                                    Function<T, String> retryAfter, Function<Duration, CompletableFuture<Void>> delay,
                                                    int retry) {
        return request.get().handle((response, error) -> {
            Throwable cause = error;
            while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
            boolean transientFailure = cause instanceof IOException || (response != null && TEMPORARY.contains(status.applyAsInt(response)));
            if (retry < 2 && transientFailure) {
                Duration duration = retryDelay(response == null ? "" : retryAfter.apply(response), retry);
                return delay.apply(duration).thenCompose(unused -> attempt(request, status, retryAfter, delay, retry + 1));
            }
            if (cause != null) return CompletableFuture.<T>failedFuture(cause);
            return CompletableFuture.completedFuture(response);
        }).thenCompose(Function.identity());
    }

    /**
     * The future, but failing with a plain message once the limit passes; the hung call is cancelled. A slow answer is not
     * retried: a second one would be just as slow and paid twice.
     */
    public static <T> CompletableFuture<T> bounded(CompletableFuture<T> call, Duration limit, String message) {
        CompletableFuture<T> result = new CompletableFuture<>();
        call.whenComplete((value, error) -> { if (error != null) result.completeExceptionally(error); else result.complete(value); });
        CompletableFuture.delayedExecutor(limit.toMillis(), TimeUnit.MILLISECONDS).execute(() -> {
            if (result.completeExceptionally(new java.util.concurrent.TimeoutException(message))) call.cancel(true);
        });
        return result;
    }

    public static Duration retryDelay(String header, int retry) {
        long seconds = 1L << Math.min(retry, 4);
        try {
            if (header != null && !header.isBlank()) {
                try { seconds = Long.parseLong(header.trim()); }
                catch (NumberFormatException ignored) {
                    seconds = Duration.between(ZonedDateTime.now(), ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)).toSeconds();
                }
            }
        } catch (RuntimeException ignored) { }
        return Duration.ofSeconds(Math.max(1, Math.min(30, seconds)));
    }
}

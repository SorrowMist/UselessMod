package com.sorrowmist.useless.utils;

import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Keeps repeated failures in recipe scans and machine ticks from flooding the log. */
public final class RateLimitedLogger {
    private static final Gate GATE = new Gate(60_000_000_000L, 256, System::nanoTime);

    private RateLimitedLogger() {}

    public static void warn(Logger logger, String message, Object... arguments) {
        if (logger.isWarnEnabled() && GATE.allow(logger.getName(), "WARN", message)) {
            logger.warn(message, arguments);
        }
    }

    public static void error(Logger logger, String message, Object... arguments) {
        if (logger.isErrorEnabled() && GATE.allow(logger.getName(), "ERROR", message)) {
            logger.error(message, arguments);
        }
    }

    static final class Gate {
        private record Key(String logger, String level, String message) {}

        private final long intervalNanos;
        private final int maximumKeys;
        private final LongSupplier clock;
        private final Map<Key, Long> lastMessages = new LinkedHashMap<>();

        Gate(long intervalNanos, int maximumKeys, LongSupplier clock) {
            if (intervalNanos <= 0 || maximumKeys <= 0) {
                throw new IllegalArgumentException("Log limits must be positive");
            }
            this.intervalNanos = intervalNanos;
            this.maximumKeys = maximumKeys;
            this.clock = clock;
        }

        synchronized boolean allow(String logger, String level, String message) {
            Key key = new Key(logger, level, message);
            long now = clock.getAsLong();
            Long previous = lastMessages.get(key);
            if (previous != null && now - previous < intervalNanos) {
                return false;
            }
            if (previous == null && lastMessages.size() >= maximumKeys) {
                // Keep existing windows intact: new categories cannot evict a hot failure
                // and make it eligible to log again in the same interval.
                lastMessages.entrySet().removeIf(entry -> now - entry.getValue() >= intervalNanos);
                if (lastMessages.size() >= maximumKeys) {
                    return false;
                }
            }
            lastMessages.put(key, now);
            return true;
        }
    }
}

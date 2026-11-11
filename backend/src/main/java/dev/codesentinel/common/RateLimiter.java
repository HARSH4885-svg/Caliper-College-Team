package dev.codesentinel.common;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/** Sliding-window limiter, in memory (lost on restart; one instance per process). Keys are client IPs. */
public class RateLimiter {
    private final int limit;
    private final long windowMillis;
    private final ConcurrentHashMap<String, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimiter(int limit, long windowMillis) {
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    public boolean allow(String key, long nowMillis) {
        if (limit <= 0) return true;
        ArrayDeque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && nowMillis - q.peekFirst() >= windowMillis) q.pollFirst();
            if (q.size() >= limit) return false;
            q.addLast(nowMillis);
        }
        if (hits.size() > 10_000) hits.entrySet().removeIf(e -> { synchronized (e.getValue()) { return e.getValue().isEmpty() || nowMillis - e.getValue().peekLast() >= windowMillis; } });
        return true;
    }
}

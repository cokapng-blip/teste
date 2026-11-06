package com.celularmod.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Limitador simples (janela deslizante) para impedir spam de mensagens/posts/curtidas. */
public final class RateLimiter {
    private final int max;
    private final long windowMs;
    private final LongSupplier clock;
    private final Map<String, long[]> buckets = new HashMap<>();

    public RateLimiter(int max, long windowMs, LongSupplier clock) {
        this.max = max;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    public synchronized boolean allow(UUID who, String action) {
        long now = clock.getAsLong();
        long[] b = buckets.computeIfAbsent(who + "/" + action, k -> new long[max]);
        // b guarda timestamps circulares; encontra o mais antigo
        int oldest = 0;
        for (int i = 1; i < max; i++) if (b[i] < b[oldest]) oldest = i;
        if (b[oldest] != 0 && now - b[oldest] < windowMs) return false;
        b[oldest] = now;
        return true;
    }

    public synchronized void forget(UUID who) {
        buckets.keySet().removeIf(k -> k.startsWith(who.toString()));
    }
}

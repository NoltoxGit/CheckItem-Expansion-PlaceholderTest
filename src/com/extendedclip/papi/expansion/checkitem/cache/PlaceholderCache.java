package com.extendedclip.papi.expansion.checkitem.cache;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Cache LRU simple (clé -> valeur) avec option de TTL (désactivé si ttlMillis <= 0).
 * Thread-safe via synchronisation sur la map interne.
 */
public class PlaceholderCache {

    private final int maxSize;
    private final long ttlMillis;
    private final Map<String, Entry> map;

    public PlaceholderCache(int maxSize, long ttlMillis) {
        this.maxSize = Math.max(50, maxSize);
        this.ttlMillis = Math.max(0, ttlMillis);
        this.map = new LinkedHashMap<>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > PlaceholderCache.this.maxSize;
            }
        };
    }

    private record Entry(Object value, long timestamp) {}

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Function<String, T> loader) {
        synchronized (map) {
            Entry e = map.get(key);
            long now = Instant.now().toEpochMilli();
            if (e != null) {
                if (ttlMillis > 0 && now - e.timestamp > ttlMillis) {
                    map.remove(key);
                } else {
                    return (T) e.value;
                }
            }
            T v = loader.apply(key);
            if (v != null) {
                map.put(key, new Entry(v, now));
            }
            return v;
        }
    }

    public void clear() {
        synchronized (map) {
            map.clear();
        }
    }
}
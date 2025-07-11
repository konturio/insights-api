package io.kontur.insightsapi.service.cacheable;

import io.kontur.insightsapi.service.IndicatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CacheCleanUpService {

    private static final Logger logger = LoggerFactory.getLogger(CacheCleanUpService.class);

    private final IndicatorService indicatorService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;

    private volatile String lastCleanUpVersion;

    /**
     * Parse version string appended to a cache key. It is either a single
     * timestamp (no indicator IDs) or a dash-separated list of
     * {@code id:timestamp} pairs.
     */
    private Map<String, String> parseVersionPairs(String version) {
        Map<String, String> result = new HashMap<>();
        for (String pair : version.split("-")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                result.put(kv[0], kv[1]);
            }
        }
        return result;
    }

    /**
     * Evicts cached data only if any indicator was updated or removed since the
     * previous cleanup. Keys that still match the current update timestamp are
     * left intact.
     */
    public synchronized void cleanUpIfRequired() {
        Instant lastUpdated = indicatorService.getIndicatorsLastUpdateDate();
        String currentGlobal = lastUpdated == null ? "none" : String.valueOf(lastUpdated.toEpochMilli());
        if (currentGlobal.equals(lastCleanUpVersion)) {
            logger.debug("Cache cleanup skipped: version {} already processed", currentGlobal);
            return;
        }

        for (String cacheName : cacheManager.getCacheNames()) {
            Set<String> keys = redisTemplate.keys(cacheName + "*");
            if (keys == null) {
                continue;
            }

            for (String key : keys) {
                String[] parts = key.split("_");
                String version = parts[parts.length - 1];

                if (version.contains(":")) {
                    Map<String, String> stored = parseVersionPairs(version);
                    Map<String, Instant> current = indicatorService.getIndicatorsLastUpdateDates(new ArrayList<>(stored.keySet()));

                    boolean outdated = stored.entrySet().stream().anyMatch(e -> {
                        Instant cur = current.get(e.getKey());
                        String curVal = cur == null ? "none" : String.valueOf(cur.toEpochMilli());
                        return !e.getValue().equals(curVal);
                    });

                    if (outdated) {
                        logger.debug("Removing outdated entry {}", key);
                        redisTemplate.delete(key);
                    }
                } else if (!version.equals(currentGlobal)) {
                    logger.debug("Removing outdated entry {}", key);
                    redisTemplate.delete(key);
                }
            }
        }
        lastCleanUpVersion = currentGlobal;
    }
}

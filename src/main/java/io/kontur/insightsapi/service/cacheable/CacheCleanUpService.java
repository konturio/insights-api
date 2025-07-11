package io.kontur.insightsapi.service.cacheable;

import io.kontur.insightsapi.service.IndicatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CacheCleanUpService {

    private final IndicatorService indicatorService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;

    private volatile String lastCleanUpVersion;

    /**
     * Evicts cached data only if any indicator was updated or removed
     * since the previous cleanup.
     */
    public synchronized void cleanUpIfRequired() {
        Instant lastUpdated = indicatorService.getIndicatorsLastUpdateDate();
        String currentGlobal = lastUpdated == null ? "none" : String.valueOf(lastUpdated.toEpochMilli());
        if (currentGlobal.equals(lastCleanUpVersion)) {
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
                    String[] pairs = version.split("-");
                    Set<String> ids = new java.util.HashSet<>();
                    for (String pair : pairs) {
                        String id = pair.split(":")[0];
                        ids.add(id);
                    }
                    var currentMap = indicatorService.getIndicatorsLastUpdateDates(new java.util.ArrayList<>(ids));
                    boolean outdated = false;
                    for (String pair : pairs) {
                        String[] kv = pair.split(":");
                        String id = kv[0];
                        String ts = kv[1];
                        Instant cur = currentMap.get(id);
                        String curVal = cur == null ? "none" : String.valueOf(cur.toEpochMilli());
                        if (!ts.equals(curVal)) {
                            outdated = true;
                            break;
                        }
                    }
                    if (outdated) {
                        redisTemplate.delete(key);
                    }
                } else if (!version.equals(currentGlobal)) {
                    redisTemplate.delete(key);
                }
            }
        }
        lastCleanUpVersion = currentGlobal;
    }
}

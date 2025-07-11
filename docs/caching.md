# Caching Overview

This document describes how caching is implemented in the Insights API and lists the current cache entries. It also suggests possible improvements.

## Cache Technology

The application uses Spring's cache abstraction backed by Redis. Redis connection settings and cache properties are defined in `application.yml`. Caches have a default time‑to‑live of 24 hours:

```yaml
spring:
  cache:
    type: redis
    cache-names: urban-core, thermal-spot, population, osm-quality, humanitarian-impact, functions, correlation-rate-all,
      metrics-num-den, metrics-num-den-with-uuid, correlation-rate-polygon, correlation-rate-polygon-all,
      metrics-num-not-empty-layers, advanced-analytics-all, advanced-analytics, covariance-rate-all, covariance-rate-polygon
    redis:
      time-to-live: 86400s
```

Feature flags under the `cache` section allow enabling or disabling cache usage for individual services.

## Key Generation

`CacheConfig` defines several `KeyGenerator` beans used by the `@Cacheable` annotations. They create stable hash-based keys for different combinations of parameters to avoid leaking sensitive values and to keep the keys short.

Only parameters of known DTO types are scanned for indicator IDs when composing
versioned cache keys. Strings or generic lists are ignored, so callers that need
per‑indicator caching must pass the IDs using `BivariateIndicatorDto`,
`NumeratorsDenominatorsDto`, `FunctionArgs` or related DTOs. The resulting key
ends with a version suffix:

```
hash_part1_hash_part2_id1:timestamp-id2:timestamp
```

If no indicator IDs are found the suffix is simply a single timestamp.

## Cached Services

Caching is implemented through facade classes found in `service/cacheable/impl`. Each facade wraps a repository or transformer that performs the actual calculations. The following services use caching:

- **UrbanCoreService** (`urban-core` cache)
- **ThermalSpotStatisticService** (`thermal-spot` cache)
- **PopulationService** (`population` cache)
- **OsmQualityService** (`osm-quality` cache)
- **HumanitarianImpactService** (`humanitarian-impact` cache)
- **FunctionsService** (`functions` cache)
- **CorrelationRateService** (`correlation-rate-*` caches)
- **CovarianceRateService** (`covariance-rate-*` caches)
- **MetricsService** (`metrics-*` caches)
- **AdvancedAnalyticsService** (`advanced-analytics*` caches)

Each service method is annotated with `@Cacheable` and `@RedisLock` to ensure that only one pod performs an expensive operation when the cache is cold. The `RedisLockAspect` implements a simple distributed lock using Redis keys prefixed with `lock::`.


## Cache Clean Up

`CacheController` delegates cleanup to `CacheCleanUpService` through the `/cache/cleanUp` endpoint. Cache keys include update timestamps for the indicators used to compute the value. During cleanup the service compares these timestamps with the current ones and removes only the keys referencing outdated or deleted indicators so valid entries survive the purge. Keys whose version suffix still matches the latest timestamp are kept intact.

## Suggested Improvements

- **Metrics**: Monitor cache hit ratios per service to tune expiration times and key design.
- **Granular invalidation**: Currently eviction clears entire caches. Investigate evicting by key when possible to reduce cache churn.
- **Configuration**: Move cache TTL values to dedicated configuration properties so they can be adjusted without redeploying the application.
- **Fallback**: Consider falling back to repository methods when Redis is unavailable instead of failing the request.

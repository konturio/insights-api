package io.kontur.insightsapi.service.cacheable.impl;

import io.kontur.insightsapi.dto.NumeratorsDenominatorsDto;
import io.kontur.insightsapi.repository.StatisticRepository;
import io.kontur.insightsapi.service.cacheable.CacheEvictable;
import io.kontur.insightsapi.service.cacheable.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Primary
@ConditionalOnProperty(prefix = "cache", name = "metrics")
@RequiredArgsConstructor
public class MetricsFacade implements MetricsService, CacheEvictable {

    private final StatisticRepository repository;

    @SneakyThrows
    @Override
    @Cacheable(value = "metrics-num-den", key = "'all'")
    public List<NumeratorsDenominatorsDto> getNumeratorsDenominatorsForMetrics() {
        return repository.getNumeratorsDenominatorsForCorrelation();
    }

    @SneakyThrows
    @Override
    @Cacheable(value = "metrics-num-not-empty-layers", keyGenerator = "stringListKeyGenerator")
    public Map<String, Boolean> getNumeratorsForNotEmptyLayersBatch(String polygon, List<NumeratorsDenominatorsDto> dtoList) {
        return repository.getNumeratorsForNotEmptyLayersBatch(polygon, dtoList);
    }

    @Override
    @CacheEvict(value = {"metrics-num-den", "metrics-num-not-empty-layers"},
            allEntries = true)
    public void evict() {
    }
}

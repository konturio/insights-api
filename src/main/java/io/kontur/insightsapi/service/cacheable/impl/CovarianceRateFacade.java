package io.kontur.insightsapi.service.cacheable.impl;

import io.kontur.insightsapi.dto.NumeratorsDenominatorsDto;
import io.kontur.insightsapi.model.PolygonMetrics;
import io.kontur.insightsapi.repository.StatisticRepository;
import io.kontur.insightsapi.service.cacheable.CacheEvictable;
import io.kontur.insightsapi.service.cacheable.CovarianceRateService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
@ConditionalOnProperty(prefix = "cache", name = "covariance-rate")
@RequiredArgsConstructor
public class CovarianceRateFacade implements CovarianceRateService, CacheEvictable {

    private final StatisticRepository repository;

    @SneakyThrows
    @Override
    @Cacheable(value = "covariance-rate-all", key = "'all'")
    public List<PolygonMetrics> getAllCovarianceRateStatistics() {
        return repository.getAllCovarianceRateStatistics();
    }

    @SneakyThrows
    @Override
    @Cacheable(value = "covariance-rate-polygon", keyGenerator = "stringListKeyGenerator")
    public List<Double> getPolygonCovarianceRateStatisticsBatch(String polygon, List<NumeratorsDenominatorsDto> dtoList) {
        return repository.getPolygonCovarianceRateStatisticsBatch(polygon, dtoList);
    }

    @Override
    @CacheEvict(value = {"covariance-rate-all", "covariance-rate-polygon"},
            allEntries = true)
    public void evict() {
    }
}

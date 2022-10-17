package io.kontur.insightsapi.service.cacheable;

import io.kontur.insightsapi.dto.NumeratorsDenominatorsDto;
import io.kontur.insightsapi.model.PolygonMetrics;

import java.util.List;

public interface CovarianceRateService {

    List<PolygonMetrics> getAllCovarianceRateStatistics();

    List<Double> getPolygonCovarianceRateStatisticsBatch(String polygon, List<NumeratorsDenominatorsDto> dtoList);
}

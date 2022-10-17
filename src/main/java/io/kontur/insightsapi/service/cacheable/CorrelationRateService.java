package io.kontur.insightsapi.service.cacheable;

import io.kontur.insightsapi.dto.NumeratorsDenominatorsDto;
import io.kontur.insightsapi.model.PolygonMetrics;

import java.util.List;

public interface CorrelationRateService {

    List<PolygonMetrics> getAllCorrelationRateStatistics();

    List<Double> getPolygonCorrelationRateStatisticsBatch(String polygon, List<NumeratorsDenominatorsDto> dtoList);
}

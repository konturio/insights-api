package io.kontur.insightsapi.service.cacheable;

import io.kontur.insightsapi.dto.NumeratorsDenominatorsDto;

import java.util.List;
import java.util.Map;

public interface MetricsService {

    List<NumeratorsDenominatorsDto> getNumeratorsDenominatorsForMetrics();

    Map<String, Boolean> getNumeratorsForNotEmptyLayersBatch(String polygon, List<NumeratorsDenominatorsDto> dtoList);
}
